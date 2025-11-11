/**
 * 独立点赞按钮组件
 * 可以在任何地方使用，不依赖特定的容器结构
 */
class LikeButtonComponent {
    constructor(options = {}) {
        this.entityType = options.entityType || 'CONTENT';
        this.entityId = options.entityId;
        this.container = options.container; // DOM元素或选择器
        this.isLiked = false;
        this.likeCount = 0;
        this.isAuthenticated = options.isAuthenticated || false;
        this.onLikeChange = options.onLikeChange || null; // 回调函数
        this.onError = options.onError || null; // 错误回调函数
        
        if (!this.entityId) {
            throw new Error('LikeButtonComponent: entityId is required');
        }

        if (!this.container) {
            throw new Error('LikeButtonComponent: container is required');
        }

        // 验证container是否为有效的DOM元素
        if (typeof this.container === 'string') {
            this.container = document.querySelector(this.container);
        }

        if (!this.container || !(this.container instanceof Element)) {
            throw new Error('LikeButtonComponent: container must be a valid DOM element');
        }
        
        this.init();
    }
    
    init() {
        this.render();
        // bindEvents() 已经在 render() 中调用（如果发现现有按钮的话）
        // 所以这里不再重复调用
        this.checkLikeStatus();
    }
    
    render() {
        const container = typeof this.container === 'string' 
            ? document.querySelector(this.container) 
            : this.container;
            
        if (!container) {
            console.error('LikeButtonComponent: container not found');
            return;
        }
        
        
        // 检查容器是否已经有静态按钮
        const existingButton = container.querySelector('.like-btn');
        if (existingButton) {
            // 如果找到子元素中的like-btn
            this.button = existingButton;
            this.icon = existingButton.querySelector('i');
            this.bindEvents();
            return;
        } else if (container.classList.contains('like-btn')) {
            // 如果容器本身就是like-btn（新的HTML结构）
            this.button = container;
            this.icon = container.querySelector('i');
            this.bindEvents();
            return;
        }
        
        // 清空容器
        container.innerHTML = '';
        
        // 创建点赞按钮
        const button = document.createElement('div');
        button.className = 'like-button-component';
        button.innerHTML = `
            <div class="like-btn cursor-pointer transition-all duration-200 p-2 rounded-full" title="点赞用户">
                <i class="fa-regular fa-heart text-gray-400 hover:text-red-500 text-xl transition-colors duration-200"></i>
            </div>
        `;
        
        container.appendChild(button);
        
        // 保存按钮引用
        this.button = button.querySelector('.like-btn');
        this.icon = this.button.querySelector('i');
        
    }
    
    bindEvents() {
        if (!this.button) {
            console.error('LikeButtonComponent: button not found for binding events');
            return;
        }

        console.log('LikeButtonComponent: binding click event to button:', this.button);

        // 保存事件处理器引用以便后续移除
        this._clickHandler = (e) => {
            console.log('LikeButtonComponent: click event triggered', {
                target: e.target,
                currentTarget: e.currentTarget,
                button: this.button,
                entityType: this.entityType,
                entityId: this.entityId
            });

            // 阻止事件冒泡和默认行为，确保不会触发卡片点击
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            
            this.toggleLike();
        };

        // 将事件绑定在整个按钮容器上，而不仅仅是图标
        // 使用捕获阶段，确保在卡片点击事件之前触发
        this.button.addEventListener('click', this._clickHandler, true);
        
        // 如果图标存在，也绑定事件以确保点击图标也能触发
        if (this.icon) {
            this.icon.addEventListener('click', this._clickHandler, true);
        }

        console.log('LikeButtonComponent: click event bound successfully');
    }
    
    async checkLikeStatus() {
        
        if (!this.isAuthenticated) {
            this.updateButton();
            return;
        }
        
        try {
            // 使用绝对路径确保在任何页面都能正确访问
            const baseUrl = window.location.origin;
            const checkUrl = `${baseUrl}/likes/check?entityType=${this.entityType}&entityId=${this.entityId}`;

            console.log('检查点赞状态请求:', checkUrl);

            const fetchOptions = {
                method: 'GET',
                headers: {
                    'Accept': 'application/json',
                },
                credentials: 'same-origin' // 确保发送session cookie
            };

            let controller = null;
            let timeoutId = null;
            if (typeof AbortController !== 'undefined') {
                controller = new AbortController();
                fetchOptions.signal = controller.signal;
                timeoutId = setTimeout(() => controller.abort(), 5000);
            }

            const response = await fetch(checkUrl, fetchOptions);
            if (timeoutId) {
                clearTimeout(timeoutId);
            }
            
            
            if (!response.ok) {
                let errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                try {
                    const errorData = await response.json();
                    if (errorData && typeof errorData === 'object' && 'message' in errorData) {
                        errorMessage = errorData.message;
                    }
                } catch (parseError) {
                    try {
                        errorMessage = await response.text();
                    } catch (_) {
                        // ignore
                    }
                }
                throw new Error(errorMessage);
            }

            const data = await response.json();
            
            this.isLiked = data.isLiked || false;
            this.likeCount = data.likeCount || 0;
            this.updateButton();
        } catch (error) {
            
            // 设置默认状态，确保按钮仍然可用
            this.isLiked = false;
            this.likeCount = 0;
            this.updateButton();
            
            if (this.onError) {
                this.onError('检查点赞状态失败', error);
            }
            console.error('检查点赞状态失败:', error);
        }
    }
    
    async toggleLike() {
        console.log('LikeButtonComponent: toggleLike called for', {
            entityType: this.entityType,
            entityId: this.entityId,
            isAuthenticated: this.isAuthenticated
        });

        if (!this.isAuthenticated) {
            console.warn('LikeButtonComponent: user not authenticated');
            this.showMessage('请先登录', 'error');
            return;
        }

        if (!this.button) {
            console.error('LikeButtonComponent: button not found');
            return;
        }

        console.log('LikeButtonComponent: starting like toggle process');

        // 显示加载状态
        this.button.style.pointerEvents = 'none';
        this.icon.className = 'fa-solid fa-spinner fa-spin text-gray-500';
        
        try {
            // 使用绝对路径确保在任何页面都能正确访问
            const baseUrl = window.location.origin;
            const toggleUrl = `${baseUrl}/likes/toggle`;

            console.log('发送点赞请求到:', toggleUrl);
            console.log('请求数据:', {
                entityType: this.entityType,
                entityId: this.entityId
            });

            const fetchOptions = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                credentials: 'same-origin', // 确保发送session cookie
                body: `entityType=${this.entityType}&entityId=${this.entityId}`
            };

            let controller = null;
            let timeoutId = null;
            if (typeof AbortController !== 'undefined') {
                controller = new AbortController();
                fetchOptions.signal = controller.signal;
                timeoutId = setTimeout(() => controller.abort(), 10000);
            }

            const response = await fetch(toggleUrl, fetchOptions);
            if (timeoutId) {
                clearTimeout(timeoutId);
            }

            console.log('响应状态:', response.status, response.statusText);

            if (!response.ok) {
                let message = `HTTP ${response.status}: ${response.statusText}`;
                try {
                    const errorData = await response.json();
                    if (errorData && typeof errorData === 'object') {
                        message = errorData.message || message;
                    }
                } catch (parseErr) {
                    try {
                        message = await response.text();
                    } catch (_) {
                        // ignore
                    }
                }
                console.error('HTTP错误响应:', message);
                throw new Error(message);
            }

            const data = await response.json();
            console.log('响应数据:', data);

            if (data.success) {
                this.isLiked = data.isLiked;
                this.likeCount = data.likeCount;
                this.updateButton();
                
                // 触发回调
                if (this.onLikeChange) {
                    this.onLikeChange({
                        isLiked: this.isLiked,
                        likeCount: this.likeCount,
                        entityType: this.entityType,
                        entityId: this.entityId
                    });
                }
                
                // 显示成功消息
                // this.showMessage(data.message || (this.isLiked ? '点赞成功' : '取消点赞成功'), 'success');
            } else {
                this.showMessage(data.message || '操作失败', 'error');
                if (this.onError) {
                    this.onError(data.message || '操作失败', null);
                }
            }
        } catch (error) {
            
            let errorMessage = error && error.message ? error.message : '网络错误，请重试';
            if (error.name === 'AbortError') {
                errorMessage = '请求超时，请重试';
            }
            
            this.showMessage(errorMessage, 'error');
            if (this.onError) {
                this.onError(errorMessage, error);
            }
            console.error('点赞操作失败:', error);
        } finally {
            this.button.style.pointerEvents = 'auto';
        }
    }
    
    updateButton() {
        if (!this.button || !this.icon) return;
        
        if (this.isLiked) {
            this.icon.className = 'fa-solid fa-heart text-red-500';
            // 移除所有背景色类
            this.button.classList.remove('hover:bg-red-50', 'bg-red-50');
        } else {
            this.icon.className = 'fa-regular fa-heart text-gray-400 hover:text-red-500';
            // 移除所有背景色类
            this.button.classList.remove('bg-red-50', 'hover:bg-red-50');
        }
    }
    
    showMessage(message, type) {
        // 创建消息提示
        const messageDiv = document.createElement('div');
        messageDiv.className = `fixed top-4 right-4 z-50 px-4 py-2 rounded-lg text-white text-sm transition-all duration-300 ${
            type === 'success' ? 'bg-green-500' : 'bg-red-500'
        }`;
        messageDiv.textContent = message;

        document.body.appendChild(messageDiv);

        // 3秒后移除消息
        setTimeout(() => {
            if (messageDiv.parentNode) {
                messageDiv.remove();
            }
        }, 3000);
    }
    
    // 公共方法
    getLikeStatus() {
        return {
            isLiked: this.isLiked,
            likeCount: this.likeCount
        };
    }
    
    setLikeStatus(isLiked, likeCount) {
        this.isLiked = isLiked;
        this.likeCount = likeCount;
        this.updateButton();
    }
    
    destroy() {
        if (this.button && this._clickHandler) {
            this.button.removeEventListener('click', this._clickHandler);
        }
        if (this.button && this.button.parentNode) {
            this.button.parentNode.remove();
        }
    }
}

/**
 * 便捷函数：创建点赞按钮
 * @param {Object} options 配置选项
 * @returns {LikeButtonComponent} 点赞按钮实例
 */
function createLikeButton(options) {
    return new LikeButtonComponent(options);
}

/**
 * 便捷函数：批量创建点赞按钮
 * @param {Array} configs 配置数组
 * @returns {Array} 点赞按钮实例数组
 */
function createLikeButtons(configs) {
    return configs.map(config => new LikeButtonComponent(config));
}

// 导出到全局
window.LikeButtonComponent = LikeButtonComponent;
window.createLikeButton = createLikeButton;
window.createLikeButtons = createLikeButtons;
