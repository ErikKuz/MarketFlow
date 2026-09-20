(() => {
    const counters = document.querySelectorAll("[data-unread-count]");
    if (counters.length === 0) {
        return;
    }

    const updateUnreadCount = async () => {
        try {
            const response = await fetch("/api/v1/notifications/unread-count", {
                credentials: "same-origin",
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) {
                return;
            }

            const data = await response.json();
            const count = Math.max(0, Number(data.count) || 0);
            counters.forEach(counter => {
                counter.textContent = String(count);
                counter.hidden = count === 0;
                counter.setAttribute("aria-label", `Непрочитанных уведомлений: ${count}`);
            });
        } catch (error) {
            // Счётчик не должен мешать работе страницы при временной ошибке сети.
        }
    };

    updateUnreadCount();
    window.setInterval(updateUnreadCount, 30000);
})();
