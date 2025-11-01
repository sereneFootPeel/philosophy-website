/**
 * 点赞按钮功能
 */
class LikeButton {
    constructor(containerId, entityType, entityId) {
        this.containerId = containerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.isLiked = false;
        this.likeCount = 0;
        this.init();
    }

    init() {
        this.createButton();
        this.checkLikeStatus();
    }

    createButton() {
        const container = document.getElementById(this.containerId);
        if (!container) return;

        // 检查是否是右上角悬浮按钮（通过父容器类名判断）
        const isFloatingButton = container.closest('.absolute') !== null;
        
        if (isFloatingButton) {
            // 右上角悬浮按钮样式
            container.innerHTML = `
                <div id="like-btn-${this.entityId}" 
                     class="like-btn cursor-pointer transition-all duration-200">
                    <i class="fa fa-heart-o text-gray-400 hover:text-red-500 text-sm transition-colors duration-200"></i>
                </div>
            `;
        } else {
            // 传统按钮样式
            container.innerHTML = `
                <button id="like-btn-${this.entityId}" 
                        class="like-btn flex items-center space-x-2 px-3 py-2 rounded-lg transition-all duration-200 
                               border border-gray-300 hover:border-red-300 hover:bg-red-50 
                               focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-opacity-50">
                    <i class="fa fa-heart-o text-gray-500"></i>
                    <span class="like-count text-sm text-gray-600">0</span>
                </button>
            `;
        }

        // 绑定点击事件
        const button = document.getElementById(`like-btn-${this.entityId}`);
        button.addEventListener('click', () => this.toggleLike());
    }

    async checkLikeStatus() {
        try {
            const response = await fetch(`/likes/check?entityType=${this.entityType}&entityId=${this.entityId}`, {
                credentials: 'same-origin' // 确保发送session cookie
            });

            if (response.status === 401) {
                // 用户未登录，设置默认状态
                this.isLiked = false;
                this.likeCount = 0;
                this.updateButton();
                return;
            }

            const data = await response.json();

            this.isLiked = data.isLiked || false;
            this.likeCount = data.likeCount || 0;
            this.updateButton();
        } catch (error) {
            // 设置默认状态以防出错
            this.isLiked = false;
            this.likeCount = 0;
            this.updateButton();
        }
    }

    async toggleLike() {
        const button = document.getElementById(`like-btn-${this.entityId}`);
        const icon = button.querySelector('i');
        const countSpan = button.querySelector('.like-count');

        // 显示加载状态
        button.disabled = true;
        icon.className = 'fa fa-spinner fa-spin text-gray-500';

        try {
            const response = await fetch('/likes/toggle', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                credentials: 'same-origin', // 确保发送session cookie
                body: `entityType=${this.entityType}&entityId=${this.entityId}`
            });

            if (response.status === 401) {
                // 用户未登录
                this.showMessage('请先登录后再点赞', 'error');
                // 可以选择重定向到登录页面
                // setTimeout(() => {
                //     window.location.href = '/login?redirect=' + encodeURIComponent(window.location.pathname);
                // }, 2000);
            } else {
                const data = await response.json();

                if (data.success) {
                    this.isLiked = data.isLiked;
                    this.likeCount = data.likeCount;
                    this.updateButton();

                    // 移除点赞成功后的绿色提示信息
                    // this.showMessage(data.message, 'success');
                } else {
                    this.showMessage(data.message || '操作失败', 'error');
                }
            }
        } catch (error) {
            this.showMessage('网络错误，请重试', 'error');
        } finally {
            button.disabled = false;
        }
    }

    updateButton() {
        const button = document.getElementById(`like-btn-${this.entityId}`);
        const icon = button.querySelector('i');
        const countSpan = button.querySelector('.like-count');

        // 检查是否是悬浮按钮（通过父容器类名判断）
        const isFloatingButton = button.closest('.absolute') !== null;

        if (this.isLiked) {
            icon.className = 'fa fa-heart text-red-500';
            if (isFloatingButton) {
                // 悬浮按钮不需要背景色
                button.classList.remove('hover:bg-red-50', 'bg-red-50');
            } else {
                button.classList.remove('border-gray-300', 'hover:border-red-300', 'hover:bg-red-50');
                button.classList.add('border-red-300');
            }
        } else {
            icon.className = isFloatingButton ? 'fa fa-heart-o text-gray-400 hover:text-red-500' : 'fa fa-heart-o text-gray-500';
            if (isFloatingButton) {
                // 悬浮按钮不需要背景色
                button.classList.remove('bg-red-50', 'hover:bg-red-50');
            } else {
                button.classList.remove('border-red-300', 'bg-red-50');
                button.classList.add('border-gray-300', 'hover:border-red-300', 'hover:bg-red-50');
            }
        }

        // 只有传统按钮才显示计数
        if (countSpan) {
            countSpan.textContent = this.likeCount;
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
            messageDiv.remove();
        }, 3000);
    }
}

/**
 * 初始化页面中的所有点赞按钮
 */
function initLikeButtons() {
    // 查找所有带有 data-like 属性的元素
    const likeElements = document.querySelectorAll('[data-like]');
    
    likeElements.forEach(element => {
        const entityType = element.getAttribute('data-entity-type');
        const entityId = element.getAttribute('data-entity-id');
        const containerId = element.getAttribute('data-like');
        
        if (entityType && entityId && containerId) {
            new LikeButton(containerId, entityType, entityId);
        }
    });
}

// 页面加载完成后初始化点赞按钮
document.addEventListener('DOMContentLoaded', initLikeButtons);
