(function () {
    function initHeaderCubes() {
        const header = document.getElementById('navbar');
        const canvas = document.getElementById('headerCubesCanvas');

        if (!header || !canvas) {
            return;
        }

        if (canvas.dataset.initialized === 'true') {
            return;
        }
        canvas.dataset.initialized = 'true';

        const ctx = canvas.getContext('2d');
        const connections = [
            [0, 1], [1, 2], [2, 3], [3, 0],
            [4, 5], [5, 6], [6, 7], [7, 4],
            [0, 4], [1, 5], [2, 6], [3, 7]
        ];

        let dpr = window.devicePixelRatio || 1;
        let width = 0;
        let height = 0;
        let cubes = [];
        let animationFrameId = null;
        let resizeObserver = null;
        let colorPalette = getThemeColors();

        class Cube {
            constructor({ size, centerX, centerY, speed, color, phase }) {
                this.size = size;
                this.centerX = centerX;
                this.centerY = centerY;
                this.speed = speed;
                this.color = color;
                this.phase = phase || 0;

                this.rotationX = Math.random() * Math.PI * 2;
                this.rotationY = Math.random() * Math.PI * 2;
                this.rotationZ = Math.random() * Math.PI * 2;

                const half = this.size / 2;
                this.points = [
                    { x: -half, y: -half, z: -half },
                    { x: half, y: -half, z: -half },
                    { x: half, y: half, z: -half },
                    { x: -half, y: half, z: -half },
                    { x: -half, y: -half, z: half },
                    { x: half, y: -half, z: half },
                    { x: half, y: half, z: half },
                    { x: -half, y: half, z: half }
                ];

                this.lineWidth = Math.max(1.1, this.size / 160);
            }

            setColor(color) {
                this.color = color;
            }

            update(elapsed) {
                const wave = Math.sin(elapsed * 0.001 + this.phase) * 0.08;
                this.rotationX += this.speed * 0.6;
                this.rotationY += this.speed;
                this.rotationZ += this.speed * 0.45;

                this.currentOffsetY = wave * this.size;
            }

            draw(ctx) {
                const projected = this.points.map((point) => {
                    let x = point.x;
                    let y = point.y;
                    let z = point.z;

                    // Rotate around X-axis
                    let cosX = Math.cos(this.rotationX);
                    let sinX = Math.sin(this.rotationX);
                    let y1 = y * cosX - z * sinX;
                    let z1 = y * sinX + z * cosX;

                    y = y1;
                    z = z1;

                    // Rotate around Y-axis
                    let cosY = Math.cos(this.rotationY);
                    let sinY = Math.sin(this.rotationY);
                    let x1 = x * cosY - z * sinY;
                    let z2 = x * sinY + z * cosY;

                    x = x1;
                    z = z2;

                    // Rotate around Z-axis
                    let cosZ = Math.cos(this.rotationZ);
                    let sinZ = Math.sin(this.rotationZ);
                    let x2 = x * cosZ - y * sinZ;
                    let y2 = x * sinZ + y * cosZ;

                    x = x2;
                    y = y2;

                    const focalLength = Math.max(220, Math.min(width, 680));
                    const scale = focalLength / (focalLength + z);

                    return {
                        x: this.centerX + x * scale,
                        y: this.centerY + this.currentOffsetY + y * scale
                    };
                });

                ctx.save();
                ctx.strokeStyle = toRgba(this.color, 0.65);
                ctx.lineWidth = this.lineWidth;
                ctx.lineCap = 'round';
                ctx.lineJoin = 'round';

                connections.forEach(([start, end]) => {
                    const a = projected[start];
                    const b = projected[end];
                    ctx.beginPath();
                    ctx.moveTo(a.x, a.y);
                    ctx.lineTo(b.x, b.y);
                    ctx.stroke();
                });
                ctx.restore();
            }
        }

        function toRgba(color, alpha) {
            if (!color) {
                return `rgba(8, 145, 178, ${alpha})`;
            }

            const trimmed = color.trim();

            if (trimmed.startsWith('#')) {
                let hex = trimmed.slice(1);
                if (hex.length === 3) {
                    hex = hex.split('').map((char) => char + char).join('');
                }
                const r = parseInt(hex.slice(0, 2), 16);
                const g = parseInt(hex.slice(2, 4), 16);
                const b = parseInt(hex.slice(4, 6), 16);
                return `rgba(${r}, ${g}, ${b}, ${alpha})`;
            }

            if (trimmed.startsWith('rgb')) {
                const parts = trimmed
                    .replace(/rgba?\(/, '')
                    .replace(')', '')
                    .split(',')
                    .map((part) => part.trim());

                const r = parts[0] || '8';
                const g = parts[1] || '145';
                const b = parts[2] || '178';
                return `rgba(${r}, ${g}, ${b}, ${alpha})`;
            }

            return trimmed;
        }

        function getThemeColors() {
            const styles = getComputedStyle(document.documentElement);
            const primary = styles.getPropertyValue('--color-primary').trim() || '#0891B2';
            const secondary = styles.getPropertyValue('--color-secondary').trim() || primary;
            const accent = styles.getPropertyValue('--color-primary-light').trim() || primary;
            return [primary, secondary, accent];
        }

        function resize() {
            const rect = header.getBoundingClientRect();
            if (rect.width === 0 || rect.height === 0) {
                return;
            }

            width = rect.width;
            height = rect.height;
            dpr = window.devicePixelRatio || 1;

            canvas.width = width * dpr;
            canvas.height = height * dpr;
            canvas.style.width = `${width}px`;
            canvas.style.height = `${height}px`;

            ctx.setTransform(1, 0, 0, 1, 0, 0);
            ctx.scale(dpr, dpr);

            regenerateCubes();
        }

        function regenerateCubes() {
            const baseSize = Math.max(24, Math.min(width, 640) * 0.18);
            const cubeCount = width < 640 ? 2 : 4;
            const centerY = height / 2;

            cubes = [];
            for (let i = 0; i < cubeCount; i++) {
                const spreadFactor = width * 0.2;
                const centerX = (width / (cubeCount + 1)) * (i + 1) + (Math.random() - 0.5) * spreadFactor * 0.4;
                const sizeVariation = baseSize * (0.7 + Math.random() * 0.6);
                const speed = 0.004 + Math.random() * 0.008;
                const color = colorPalette[i % colorPalette.length] || colorPalette[0];
                const phase = Math.random() * Math.PI * 2;

                cubes.push(
                    new Cube({
                        size: sizeVariation,
                        centerX,
                        centerY,
                        speed,
                        color,
                        phase
                    })
                );
            }
        }

        function animate(timestamp) {
            ctx.clearRect(0, 0, width, height);

            cubes.forEach((cube) => {
                cube.update(timestamp);
                cube.draw(ctx);
            });

            animationFrameId = requestAnimationFrame(animate);
        }

        function updateColors() {
            colorPalette = getThemeColors();
            cubes.forEach((cube, index) => {
                cube.setColor(colorPalette[index % colorPalette.length] || colorPalette[0]);
            });
        }

        function start() {
            resize();
            cancelAnimationFrame(animationFrameId);
            animationFrameId = requestAnimationFrame(animate);
        }

        function handleVisibilityChange() {
            if (document.hidden) {
                cancelAnimationFrame(animationFrameId);
                animationFrameId = null;
            } else if (!animationFrameId) {
                animationFrameId = requestAnimationFrame(animate);
            }
        }

        window.addEventListener('resize', resize);
        document.addEventListener('themeChange', updateColors);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        if (typeof ResizeObserver !== 'undefined') {
            resizeObserver = new ResizeObserver(() => {
                resize();
            });
            resizeObserver.observe(header);
        }

        start();

        // Cleanup when the page unloads
        window.addEventListener('beforeunload', () => {
            cancelAnimationFrame(animationFrameId);
            if (resizeObserver) {
                resizeObserver.disconnect();
            }
            window.removeEventListener('resize', resize);
            document.removeEventListener('themeChange', updateColors);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initHeaderCubes);
    } else {
        initHeaderCubes();
    }
})();

