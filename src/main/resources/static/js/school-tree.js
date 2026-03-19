document.addEventListener('DOMContentLoaded', function () {
    const tree = document.getElementById('school-tree');
    if (!tree) return;
    const rootUl = document.getElementById('school-tree-root');
    const detail = document.getElementById('school-detail');
    const titleEl = document.getElementById('school-title');
    const descEl = document.getElementById('school-desc');
    const viewLink = document.getElementById('school-view-link');
    const contentsEl = document.getElementById('school-contents');
    const emptyEl = document.getElementById('school-empty');

    // 无限滚动相关变量
    let currentSchoolId = null;
    let currentPage = 0;
    let isLoading = false;
    let hasMore = true;

    // 一次性缓存：所有流派名字节点（点击展开时仅在前端渲染，不再请求 children）
    /** @type {Map<string, any>} */
    const nodeById = new Map();
    /** @type {Map<string|null, any[]>} */
    const childrenByParentId = new Map();

    // 初始化：加载所有名字节点并渲染顶级节点
    initTree().catch(err => console.error('初始化流派树失败', err));

    async function initTree() {
        if (!rootUl) return;
        const loadingLi = document.getElementById('school-tree-loading');
        if (loadingLi) loadingLi.textContent = '加载中...';

        const resp = await fetch('/api/schools/nodes');
        if (!resp.ok) throw new Error('Network error');
        const nodes = await resp.json();

        nodeById.clear();
        childrenByParentId.clear();

        if (Array.isArray(nodes)) {
            for (const n of nodes) {
                if (!n || n.id == null) continue;
                const id = String(n.id);
                const parentKey = (n.parentId == null) ? null : String(n.parentId);
                nodeById.set(id, n);
                if (!childrenByParentId.has(parentKey)) {
                    childrenByParentId.set(parentKey, []);
                }
                childrenByParentId.get(parentKey).push(n);
            }
        }

        // 按 sortKey 排序每个父节点下的子列表（后端提供，避免前端拼音计算）
        for (const [, list] of childrenByParentId) {
            list.sort((a, b) => String(a.sortKey || '').localeCompare(String(b.sortKey || '')));
        }

        // 渲染顶级节点
        rootUl.innerHTML = '';
        const topList = childrenByParentId.get(null) || [];
        if (topList.length === 0) {
            const li = document.createElement('li');
            li.className = 'text-sm text-gray-500 px-3 py-2';
            li.textContent = '暂无流派';
            rootUl.appendChild(li);
        } else {
            for (const node of topList) {
                rootUl.appendChild(renderNodeLi(node));
            }
        }

        // 初始化完成后，如果URL里带了选中的流派ID，则自动定位并展开路径
        if (window.selectedSchoolId) {
            setTimeout(() => {
                findAndSelectSchool(String(window.selectedSchoolId));
            }, 50);
        }
    }

    function getDirectSchoolLink(li) {
        if (!li || !li.children) return null;
        for (let i = 0; i < li.children.length; i++) {
            const child = li.children[i];
            if (child.classList && child.classList.contains('school-link')) {
                return child;
            }
        }
        return null;
    }

    function toggleExpandIcon(linkElement, isExpanded) {
        const expandIcon = linkElement.querySelector('.expand-icon');
        if (expandIcon) {
            if (isExpanded) {
                expandIcon.style.transform = 'rotate(90deg)';
            } else {
                expandIcon.style.transform = 'rotate(0deg)';
            }
        }
    }

    function showExpandIcon(linkElement, hasChildren) {
        const expandIcon = linkElement.querySelector('.expand-icon');
        if (expandIcon) {
            expandIcon.style.display = hasChildren ? 'inline-block' : 'none';
        }
    }

    tree.addEventListener('click', async function (e) {
        const target = e.target;
        const row = target.closest('[data-id]');
        if (!row) return;

        const schoolId = row.getAttribute('data-id');
        const childrenUl = row.parentElement.querySelector('.children');

        // 计算需保留高亮的祖先（仅保留当前已高亮的祖先）
        const preserve = new Set();
        let currentLi = row.parentElement;
        while (currentLi && currentLi.tagName && currentLi.tagName.toLowerCase() !== 'li') {
            currentLi = currentLi.parentElement;
        }
        while (currentLi && currentLi.tagName && currentLi.tagName.toLowerCase() === 'li') {
            const ancestorLink = getDirectSchoolLink(currentLi);
            if (ancestorLink && ancestorLink.classList.contains('bg-primary')) {
                preserve.add(ancestorLink);
            }
            const parentUl = currentLi.parentElement; // ul
            if (!parentUl) break;
            const parentLi = parentUl.parentElement; // li of parent
            if (!parentLi || parentLi.tagName.toLowerCase() !== 'li') break;
            currentLi = parentLi;
        }

        // 单选高亮但保留祖先已有高亮
        const allRows = tree.querySelectorAll('.school-link');
        allRows.forEach(el => {
            if (el !== row && !preserve.has(el)) {
                el.classList.remove('bg-primary', 'text-white');
                el.classList.add('text-gray-700');
            }
        });
        row.classList.add('bg-primary', 'text-white');
        row.classList.remove('text-gray-700');

        // 仅在前端渲染/切换展开（不再请求 /api/schools/children）
        if (childrenUl) {
            if (childrenUl.dataset.rendered !== 'true') {
                renderChildrenInto(childrenUl, schoolId);
                childrenUl.dataset.rendered = 'true';
            }
            const hasChild = childrenUl.children.length > 0;
            showExpandIcon(row, hasChild);
            if (hasChild) {
                const isHidden = childrenUl.classList.contains('hidden');
                if (isHidden) {
                    childrenUl.classList.remove('hidden');
                    toggleExpandIcon(row, true);
                } else {
                    childrenUl.classList.add('hidden');
                    toggleExpandIcon(row, false);
                }
            } else {
                toggleExpandIcon(row, false);
            }
        }

        await loadRightPanel(schoolId);
    });

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    async function loadRightPanel(id) {
        try {
            // 重置无限滚动状态
            currentSchoolId = id;
            currentPage = 0;
            hasMore = true;
            isLoading = false;

            const d = await (await fetch(`/api/schools/detail?id=${encodeURIComponent(id)}`)).json();
            if (d && d.id) {
                const detail = document.getElementById('school-detail');
                const titleEl = document.getElementById('school-title');
                const descEl = document.getElementById('school-desc');
                const viewLink = document.getElementById('school-view-link');
                const editLink = document.getElementById('school-edit-link'); // 获取编辑链接
                if (detail) detail.classList.remove('hidden');
                if (titleEl) titleEl.textContent = d.displayName || d.name || '—';
                if (descEl) descEl.textContent = d.description || '';
                if (viewLink) viewLink.href = `/schools/filter/${d.id}`;

                // 更新编辑链接的href - 根据用户角色动态设置路径
                if (editLink) {
                    // 关键：redirectUrl 必须是站内相对路径（后端只允许以 "/" 开头），
                    // 且最好带上当前流派ID，保证保存后回到页面时能自动选中并加载右侧面板。
                    const redirectUrl = encodeURIComponent(`/schools/filter/${d.id}`);
                    // 根据用户角色选择正确的路径：版主用 /moderator，管理员用 /admin
                    const basePath = window.userRole === 'MODERATOR' ? '/moderator' : '/admin';
                    editLink.href = `${basePath}/schools/edit/${d.id}?redirectUrl=${redirectUrl}`;
                    editLink.title = `编辑ID: ${d.id}`;
                }
            }
            const contentsEl = document.getElementById('school-contents');
            const emptyEl = document.getElementById('school-empty');
            if (contentsEl) contentsEl.innerHTML = '<div class="text-center text-gray-500">加载中...</div>';
            const html = await (await fetch(`/partials/schools/contents?id=${encodeURIComponent(id)}`)).text();
            if (contentsEl) {
                contentsEl.innerHTML = html;
            }
            if (emptyEl) emptyEl.classList.add('hidden');

            // 重新初始化新加载内容中的点赞按钮和卡片点击事件
            if (typeof initLikeButtons === 'function') {
                initLikeButtons();
            }
            if (typeof initContentCardClickHandlers === 'function') {
                initContentCardClickHandlers();
            }

            // 初始化无限滚动监听器
            initInfiniteScroll();
        } catch (e) {
            console.error('加载详情失败', e);
        }
    }

    // 初始化无限滚动
    function initInfiniteScroll() {
        // 移除旧的监听器（如果存在）
        window.removeEventListener('scroll', handleScroll);
        // 添加新的监听器
        window.addEventListener('scroll', handleScroll);
    }

    // 处理滚动事件
    function handleScroll() {
        if (isLoading || !hasMore || !currentSchoolId) return;

        const contentsEl = document.getElementById('school-contents');
        if (!contentsEl) return;

        // 计算是否接近底部（距离底部800px时提前触发，让用户无感知）
        const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        const windowHeight = window.innerHeight;
        const documentHeight = document.documentElement.scrollHeight;

        if (scrollTop + windowHeight >= documentHeight - 800) {
            loadMoreContents();
        }
    }

    // 加载更多内容（静默加载，用户无感知）
    async function loadMoreContents() {
        if (isLoading || !hasMore || !currentSchoolId) return;

        isLoading = true;
        currentPage++;

        try {
            const response = await fetch(`/api/schools/contents/more?id=${encodeURIComponent(currentSchoolId)}&page=${currentPage}&size=10`);
            if (!response.ok) {
                throw new Error('加载失败');
            }

            const data = await response.json();
            
            if (data.success && data.contents && data.contents.length > 0) {
                // 获取当前语言
                const language = document.documentElement.lang || 'zh';

                // 尝试从已存在的内容卡片中推断鉴权状态
                const existingLikeBtn = document.querySelector('.like-btn[data-entity-type="CONTENT"][data-is-authenticated]');
                const isAuthenticated = existingLikeBtn
                    ? existingLikeBtn.getAttribute('data-is-authenticated') === 'true'
                    : false;

                // 渲染新的内容卡片（静默追加到末尾）
                const contentsEl = document.getElementById('school-contents');

                data.contents.forEach(content => {
                    const contentCard = createContentCard(content, language, isAuthenticated);
                    contentsEl.appendChild(contentCard);
                });

                // 更新是否有更多数据
                hasMore = data.hasMore;

                // 重新初始化点赞按钮和卡片点击事件
                if (typeof initLikeButtons === 'function') {
                    initLikeButtons();
                }
                if (typeof initContentCardClickHandlers === 'function') {
                    initContentCardClickHandlers();
                }
            } else {
                hasMore = false;
            }
        } catch (error) {
            console.error('加载更多内容失败:', error);
            hasMore = false;
        } finally {
            isLoading = false;
        }
    }

    // 创建内容卡片HTML
    function createContentCard(content, language, isAuthenticated) {
        const div = document.createElement('div');
        div.className = 'content-card bg-white rounded-lg shadow-sm p-4 hover:shadow-md transition-all duration-300 relative group cursor-pointer hover:bg-tertiary content-card-clickable like-button-parent mb-4';
        div.setAttribute('data-content-id', content.id);
        div.setAttribute('data-clickable', 'true');

        const school = content.school || null;
        const parentSchool = school && school.parent ? school.parent : null;
        const philosopher = content.philosopher || null;

        // 语言敏感内容
        const schoolName = getLocalizedName(school, language);
        const parentSchoolName = getLocalizedName(parentSchool, language);
        const philosopherName = getLocalizedName(philosopher, language);
        const philosopherEra = philosopher && philosopher.era ? philosopher.era : '';
        const title = content.title ? content.title : '';
        const displayText = getContentDisplayText(content, language);

        const likeTitle = language === 'en' ? 'Like / Unlike' : '点赞/取消点赞';
        const viewMoreTitle = language === 'en' ? 'View more' : '查看更多';

        const likeButtonHtml = isAuthenticated ? `
            <div id="like-container-${content.id}"
                 data-like="like-container-${content.id}"
                 data-entity-type="CONTENT"
                 data-entity-id="${content.id}"
                 data-is-authenticated="true"
                 class="like-btn group-hover:opacity-100 lg:opacity-0 lg:group-hover:opacity-100 transition-opacity duration-300 flex items-center justify-center pointer-events-auto z-20"
                 title="${likeTitle}">
                <i class="fa-regular fa-heart text-red-500 text-base transition-colors duration-200"></i>
            </div>
        ` : '';

        const schoolLink = school ? `
            <a href="/schools/filter/${school.id}"
               class="bg-primary/10 text-primary px-3 py-1 rounded-full text-xs hover:bg-primary/20 transition-smooth">
                ${escapeHtml(schoolName)}
            </a>
        ` : '';

        const parentSchoolLink = parentSchool ? `
            <a href="/schools/filter/${parentSchool.id}"
               class="bg-gray-100 text-gray-700 px-3 py-1 rounded-full text-xs hover:bg-gray-200 transition-smooth">
                ${escapeHtml(parentSchoolName)}
            </a>
        ` : '';

        const philosopherInfo = philosopherName ? `
            <div class="flex items-center">
                <a href="/philosophers?philosopherId=${philosopher.id}"
                   class="flex items-center text-secondary hover:text-primary transition-colors duration-200">
                    <span class="text-xs font-medium">${escapeHtml(philosopherName)}</span>
                    ${philosopherEra ? `<span class="text-gray-400 mx-1 text-xs">|</span>` : ''}
                    ${philosopherEra ? `<span class="text-xs text-gray-500">${escapeHtml(philosopherEra)}</span>` : ''}
                </a>
            </div>
        ` : '';

        const viewMoreUrlParams = [];
        if (school && school.id) {
            viewMoreUrlParams.push(`schoolId=${school.id}`);
        }
        if (philosopher && philosopher.id) {
            viewMoreUrlParams.push(`philosopherId=${philosopher.id}`);
        }
        const viewMoreUrl = `/contents${viewMoreUrlParams.length ? `?${viewMoreUrlParams.join('&')}` : ''}`;

        div.innerHTML = `
            <div class="absolute top-3 right-3 z-10 transition-opacity duration-300">
                ${likeButtonHtml}
                <div class="opacity-0 group-hover:opacity-100 transition-opacity duration-300">
                    <a href="${viewMoreUrl}"
                       onclick="event.stopPropagation()"
                       title="${viewMoreTitle}"
                       class="flex items-center justify-center w-8 h-8 hover:bg-gray-100 rounded-md transition-colors duration-200">
                        <i class="fa fa-external-link text-gray-600 text-sm"></i>
                    </a>
                </div>
            </div>
            <div class="flex flex-wrap gap-2 mb-3">
                ${parentSchoolLink}
                ${schoolLink}
            </div>
            ${title ? `
                <div class="mb-2">
                    <h3 class="text-lg font-semibold text-gray-900">${escapeHtml(title)}</h3>
                </div>
            ` : ''}
            <div class="prose prose-gray max-w-none mb-4">
                <p class="text-gray-800 whitespace-pre-line">${escapeHtml(displayText).replace(/\n/g, '<br>')}</p>
            </div>
            ${philosopherInfo ? `
                <div class="flex items-center justify-between pt-3 border-t border-gray-100">
                    ${philosopherInfo}
                </div>
            ` : ''}
        `;

        return div;
    }

    function getLocalizedName(entity, language) {
        if (!entity) {
            return '';
        }
        if (language === 'en' && entity.nameEn) {
            return entity.nameEn;
        }
        if (entity.displayName) {
            return entity.displayName;
        }
        return entity.name || entity.title || '';
    }

    function getContentDisplayText(content, language) {
        if (!content) {
            return '';
        }
        if (language === 'en' && content.contentEn) {
            return content.contentEn;
        }
        return content.content || content.contentEn || '';
    }

    function renderNodeLi(node) {
        const li = document.createElement('li');
        li.className = '';
        const displayName = node.displayName || node.name || '未命名';
        const hasChildren = !!node.hasChildren;
        li.innerHTML = `
            <div class="school-link px-3 py-2.5 sm:py-2 rounded-lg transition-smooth text-gray-700 cursor-pointer flex items-center justify-between min-h-[44px] touch-manipulation" data-id="${escapeHtml(node.id)}">
                <span class="font-medium text-sm sm:text-base">${escapeHtml(displayName)}</span>
                <i class="fa fa-chevron-right expand-icon transition-transform duration-200" style="display: ${hasChildren ? 'inline-block' : 'none'};"></i>
            </div>
            <ul class="children mt-2 hidden" id="children-of-${escapeHtml(node.id)}"></ul>
        `;
        return li;
    }

    function renderChildrenInto(childrenUl, parentId) {
        if (!childrenUl) return;
        const list = childrenByParentId.get(String(parentId)) || [];
        childrenUl.innerHTML = '';
        for (const node of list) {
            const li = document.createElement('li');
            li.className = 'mt-2';
            const displayName = node.displayName || node.name || '未命名';
            const hasChildren = !!node.hasChildren;
            li.innerHTML = `
                <div class="school-link px-3 py-2 rounded-lg transition-smooth text-gray-700 cursor-pointer flex items-center justify-between" data-id="${escapeHtml(node.id)}">
                    <span class="font-medium">${escapeHtml(displayName)}</span>
                    <i class="fa fa-chevron-right expand-icon transition-transform duration-200" style="display: ${hasChildren ? 'inline-block' : 'none'};"></i>
                </div>
                <ul class="children mt-2 hidden" id="children-of-${escapeHtml(node.id)}"></ul>
            `;
            childrenUl.appendChild(li);
        }
    }

    // 查找并选中指定的流派（用于从URL跳转时自动定位）
    async function findAndSelectSchool(schoolId) {
        if (!schoolId) return;
        const targetId = String(schoolId);
        if (!nodeById.has(targetId)) return;

        // 计算从顶级到目标的路径（基于已缓存 parentId）
        const path = [];
        let cur = targetId;
        while (cur && nodeById.has(cur)) {
            path.unshift(cur);
            const n = nodeById.get(cur);
            cur = (n && n.parentId != null) ? String(n.parentId) : null;
        }

        if (path.length === 0) return;

        // 逐层展开路径
        let currentLink = tree.querySelector(`.school-link[data-id="${path[0]}"]`);
        if (!currentLink) return;

        for (let i = 0; i < path.length - 1; i++) {
            const id = path[i];
            const nextId = path[i + 1];
            const childrenUl = currentLink.parentElement.querySelector('.children');
            if (!childrenUl) return;

            if (childrenUl.dataset.rendered !== 'true') {
                renderChildrenInto(childrenUl, id);
                childrenUl.dataset.rendered = 'true';
            }
            if (childrenUl.classList.contains('hidden')) {
                childrenUl.classList.remove('hidden');
                toggleExpandIcon(currentLink, true);
            }
            const nextLink = childrenUl.querySelector(`.school-link[data-id="${nextId}"]`);
            if (!nextLink) return;
            currentLink = nextLink;
        }

        currentLink.click();
        setTimeout(() => {
            currentLink.scrollIntoView({ behavior: 'smooth', block: 'center' });
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }, 300);
    }
});


