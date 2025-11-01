// 卡片网络功能实现

// 流派卡片网络功能 - 增强版
(function() {
    console.log('school-network.js 增强版已加载');
    
    // 当DOM完全加载后执行
    document.addEventListener('DOMContentLoaded', function() {
        console.log('DOM内容已加载，初始化卡片网络功能');
        
        // 获取所有哲学家卡片
        const cards = document.querySelectorAll('.philosopher-card');
        
        if (cards.length > 0) {
            console.log('找到 ' + cards.length + ' 张哲学家卡片');
            
            // 为每张卡片添加悬停效果
            cards.forEach(card => {
                // 获取卡片中的图片元素
                const img = card.querySelector('img');
                
                if (img) {
                    // 鼠标悬停时
                    card.addEventListener('mouseenter', function() {
                        // 放大当前卡片图片（增强效果）
                        if (img) {
                            img.style.transform = 'scale(1.2)';
                            img.style.transition = 'transform 0.5s ease';
                        }
                        
                        // 放大当前卡片
                        this.style.transform = 'scale(1.05)';
                        this.style.zIndex = '10';
                        this.style.boxShadow = '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)';
                        this.style.transition = 'all 0.3s ease';
                        
                        // 获取当前卡片的流派
                        const currentSchools = getCardSchools(this);
                        
                        // 处理其他卡片
                        cards.forEach(otherCard => {
                            if (otherCard === this) return;
                            
                            const otherSchools = getCardSchools(otherCard);
                            const otherImg = otherCard.querySelector('img');
                            
                            // 检查是否有共同流派
                            const hasCommon = currentSchools.some(school => otherSchools.includes(school));
                            
                            if (hasCommon) {
                                // 高亮显示相关卡片
                                otherCard.classList.add('related-card');
                                otherCard.classList.remove('unrelated-card');
                                // 相关卡片图片也略微放大
                                if (otherImg) {
                                    otherImg.style.transform = 'scale(1.1)';
                                }
                            } else {
                                // 淡出显示不相关卡片
                                otherCard.classList.add('unrelated-card');
                                otherCard.classList.remove('related-card');
                                // 不相关卡片图片保持原状
                                if (otherImg) {
                                    otherImg.style.transform = 'scale(1)';
                                }
                            }
                        });
                    });
                    
                    // 鼠标离开时
                    card.addEventListener('mouseleave', function() {
                        // 恢复当前卡片图片
                        if (img) {
                            img.style.transform = 'scale(1.1)'; // 保持轻微放大效果
                            img.style.transition = 'transform 0.5s ease';
                        }
                        
                        // 恢复当前卡片
                        this.style.transform = '';
                        this.style.zIndex = '';
                        this.style.boxShadow = '';
                        
                        // 恢复所有其他卡片
                        cards.forEach(otherCard => {
                            otherCard.classList.remove('related-card', 'unrelated-card');
                            const otherImg = otherCard.querySelector('img');
                            if (otherImg) {
                                otherImg.style.transform = 'scale(1.1)'; // 保持轻微放大效果
                            }
                        });
                    });
                }
            });
        }
    });
    
    // 辅助函数：获取卡片上的所有流派
    function getCardSchools(card) {
        const schoolTags = card.querySelectorAll('.school-tag');
        return Array.from(schoolTags).map(tag => tag.textContent.trim());
    }
    
    console.log('卡片网络功能初始化完成');
})();