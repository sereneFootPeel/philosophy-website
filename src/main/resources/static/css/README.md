# 主题系统使用说明

## 概述

本项目实现了统一的主题管理系统，支持浅色、深色和自动三种主题模式。所有颜色通过CSS变量统一管理，便于维护和扩展。

## 文件结构

```
src/main/resources/
├── static/
│   └── css/
│       ├── theme.css          # 主题样式文件（包含所有颜色变量）
│       └── README.md          # 本文件
└── templates/
    ├── fragments/
    │   ├── header.html        # 页面头部（引入theme.css）
    │   └── footer.html        # 页面底部（包含主题管理器脚本）
    └── user/
        └── profile.html       # 用户主页（主题切换UI）
```

## 主题变量说明

### 颜色变量

#### 主色调
- `--color-primary`: 主色调
- `--color-primary-dark`: 主色调深色变体
- `--color-primary-light`: 主色调浅色变体

#### 背景色
- `--bg-primary`: 主要背景色
- `--bg-secondary`: 次要背景色
- `--bg-tertiary`: 第三背景色
- `--bg-quaternary`: 第四背景色

#### 文本色
- `--text-primary`: 主要文本色
- `--text-secondary`: 次要文本色
- `--text-tertiary`: 第三文本色
- `--text-quaternary`: 第四文本色

#### 边框色
- `--border-primary`: 主要边框色
- `--border-secondary`: 次要边框色
- `--border-tertiary`: 第三边框色

#### 状态色
- `--color-success`: 成功色
- `--color-error`: 错误色
- `--color-warning`: 警告色
- `--color-info`: 信息色

### 其他变量

- `--shadow-*`: 阴影样式
- `--transition-*`: 过渡动画时长
- `--radius-*`: 圆角大小

## 使用方法

### 1. 在HTML中使用CSS变量

```html
<div class="card">
    <h2 class="text-primary">标题</h2>
    <p class="text-secondary">内容</p>
</div>
```

### 2. 在CSS中使用CSS变量

```css
.custom-element {
    background-color: var(--bg-primary);
    color: var(--text-primary);
    border: 1px solid var(--border-primary);
}
```

### 3. 切换主题

主题切换功能已集成到用户主页。用户可以在设置中选择：
- **浅色主题**: 明亮的白色背景
- **深色主题**: 深色背景，适合夜间使用
- **自动主题**: 根据系统设置自动切换

### 4. 在JavaScript中操作主题

```javascript
// 获取当前主题
const currentTheme = GlobalThemeManager.getCurrentTheme();

// 设置主题
GlobalThemeManager.setTheme('dark');

// 监听主题变化
document.addEventListener('themeChange', function(event) {
    console.log('主题已切换为:', event.detail.theme);
});
```

## 主题切换UI

在用户主页（`/user/profile`）的设置区域，有三个主题切换按钮：

```html
<button class="theme-btn" data-theme="light">
    <i class="fa fa-sun-o"></i>
    <span>浅色</span>
</button>
<button class="theme-btn" data-theme="dark">
    <i class="fa fa-moon-o"></i>
    <span>深色</span>
</button>
<button class="theme-btn" data-theme="auto">
    <i class="fa fa-adjust"></i>
    <span>自动</span>
</button>
```

## 主题持久化

主题设置会自动保存到浏览器的 `localStorage` 中，键名为 `philosophy_theme`。用户刷新页面后，主题设置会自动恢复。

## 添加新主题

要添加新的主题，请在 `theme.css` 中添加新的主题类：

```css
.theme-custom {
    --color-primary: #YOUR_COLOR;
    --bg-primary: #YOUR_BG_COLOR;
    /* 其他变量... */
}
```

然后在主题管理器中注册新主题：

```javascript
themes: {
    light: '浅色',
    dark: '深色',
    auto: '自动',
    custom: '自定义'  // 新增
}
```

## 最佳实践

1. **始终使用CSS变量**: 不要使用硬编码的颜色值，而是使用CSS变量
2. **保持一致性**: 确保所有页面使用相同的主题系统
3. **测试对比度**: 确保浅色和深色主题都有足够的对比度
4. **平滑过渡**: 主题切换时使用CSS过渡动画，提升用户体验

## 浏览器兼容性

- Chrome/Edge: 完全支持
- Firefox: 完全支持
- Safari: 完全支持
- IE11: 不支持（CSS变量不支持）

## 常见问题

### Q: 为什么我的自定义样式没有应用主题？
A: 确保你使用了CSS变量而不是硬编码的颜色值。

### Q: 如何为特定组件添加主题支持？
A: 在 `theme.css` 中为你的组件添加样式，使用CSS变量。

### Q: 主题切换后页面闪烁怎么办？
A: 确保 `theme.css` 在页面头部加载，主题类在页面加载时立即应用。

## 更新日志

### v1.0.0 (2025-01-XX)
- 初始版本
- 支持浅色、深色和自动主题
- 统一的颜色管理系统
- 主题持久化功能

## 贡献指南

如果你想要改进主题系统，请：

1. 修改 `theme.css` 中的CSS变量
2. 更新 `footer.html` 中的主题管理器（如需要）
3. 测试所有主题模式
4. 更新本README文档

## 许可证

本主题系统是项目的一部分，遵循项目整体许可证。

