document.addEventListener('DOMContentLoaded', function () {
    const tree = document.getElementById('school-tree');
    if (!tree) return;
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

    // 初始化顶级流派的展开图标
    initializeTopLevelSchools();

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

    async function initializeTopLevelSchools() {
        const topLevelLinks = tree.querySelectorAll('.school-link[data-id]');
        for (const link of topLevelLinks) {
            const schoolId = link.getAttribute('data-id');
            try {
                // 检查是否有子流派
                const resp = await fetch(`/api/schools/children?parentId=${encodeURIComponent(schoolId)}`);
                if (resp.ok) {
                    const data = await resp.json();
                    const hasChildren = Array.isArray(data) && data.length > 0;
                    showExpandIcon(link, hasChildren);
                }
            } catch (err) {
                console.error('检查子流派失败', err);
                showExpandIcon(link, false);
            }
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

        // 已加载则仅折叠/展开
        if (childrenUl && childrenUl.dataset.loaded === 'true') {
            const isHidden = childrenUl.classList.contains('hidden');
            if (isHidden) {
                childrenUl.classList.remove('hidden');
                toggleExpandIcon(row, true);
            } else {
                childrenUl.classList.add('hidden');
                toggleExpandIcon(row, false);
            }
            // 只有在展开时才加载右侧面板
            if (!isHidden) {
                await loadRightPanel(schoolId);
            }
            return;
        }

        // 异步加载子流派（无hover类）
        if (childrenUl && childrenUl.dataset.loaded !== 'true') {
            try {
                const resp = await fetch(`/api/schools/children?parentId=${encodeURIComponent(schoolId)}`);
                if (!resp.ok) throw new Error('Network error');
                const data = await resp.json();
                childrenUl.innerHTML = '';
                if (Array.isArray(data) && data.length > 0) {
                    for (const node of data) {
                        const li = document.createElement('li');
                        li.className = 'mt-2';
                        const displayName = node.displayName || node.name || '未命名';
                        const hasChildren = node.hasChildren || false;
                        li.innerHTML = `
                            <div class="school-link px-3 py-2 rounded-lg transition-smooth text-gray-700 cursor-pointer flex items-center justify-between" data-id="${node.id}">
                                <span class="font-medium">${escapeHtml(displayName)}</span>
                                <i class="fa fa-chevron-right expand-icon transition-transform duration-200" style="display: ${hasChildren ? 'inline-block' : 'none'};"></i>
                            </div>
                            <ul class="children mt-2 hidden" id="children-of-${node.id}"></ul>
                        `;
                        childrenUl.appendChild(li);
                    }
                    childrenUl.dataset.loaded = 'true';
                    // 第一次加载时展开显示子流派
                    childrenUl.classList.remove('hidden');
                    toggleExpandIcon(row, true);
                } else {
                    childrenUl.dataset.loaded = 'true';
                    // 没有子项，不显示展开图标
                    toggleExpandIcon(row, false);
                }
            } catch (err) {
                console.error('加载子流派失败', err);
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
                    const redirectUrl = encodeURIComponent(window.location.href);
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
                
                // 渲染新的内容卡片（静默追加到末尾）
                const contentsEl = document.getElementById('school-contents');
                
                data.contents.forEach(content => {
                    const contentCard = createContentCard(content, language);
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
    function createContentCard(content, language) {
        const div = document.createElement('div');
        div.className = 'content-card bg-white rounded-lg shadow-sm p-4 mb-4 cursor-pointer hover:shadow-md transition-shadow';
        div.setAttribute('data-content-id', content.id);
        
        // 获取哲学家名称
        const philosopherName = content.philosopher ? 
            (language === 'en' && content.philosopher.nameEn ? content.philosopher.nameEn : content.philosopher.name) : '';
        
        // 获取流派名称
        const schoolName = content.school ? 
            (language === 'en' && content.school.nameEn ? content.school.nameEn : content.school.name) : '';
        
        // 获取作者信息
        const authorName = content.user ? content.user.username : '匿名';
        const authorRole = content.user ? content.user.role : '';
        let roleTag = '';
        if (authorRole === 'ADMIN') {
            roleTag = '<span class="ml-2 px-2 py-0.5 bg-red-100 text-red-700 text-xs rounded">管理员</span>';
        } else if (authorRole === 'MODERATOR') {
            roleTag = '<span class="ml-2 px-2 py-0.5 bg-blue-100 text-blue-700 text-xs rounded">版主</span>';
        }
        
        // 内容预览（限制长度）
        const contentText = content.content || '';
        const contentPreview = contentText.length > 200 ? contentText.substring(0, 200) + '...' : contentText;
        
        div.innerHTML = `
            <div class="flex justify-between items-start mb-2">
                <div class="flex-1">
                    <h3 class="text-lg font-semibold text-primary mb-1">${escapeHtml(content.title || philosopherName)}</h3>
                    <div class="text-sm text-gray-500">
                        <span>${escapeHtml(philosopherName)}</span>
                        ${schoolName ? `<span class="mx-2">•</span><span>${escapeHtml(schoolName)}</span>` : ''}
                    </div>
                </div>
                <div class="flex items-center space-x-2">
                    <like-button entity-type="content" entity-id="${content.id}" like-count="${content.likeCount || 0}"></like-button>
                </div>
            </div>
            <p class="text-gray-700 mb-3">${escapeHtml(contentPreview)}</p>
            <div class="flex justify-between items-center text-sm text-gray-500">
                <span>
                    作者: ${escapeHtml(authorName)}${roleTag}
                </span>
                ${content.commentCount ? `<span><i class="fa fa-comment mr-1"></i>${content.commentCount} 条评论</span>` : ''}
            </div>
        `;
        
        return div;
    }
});


