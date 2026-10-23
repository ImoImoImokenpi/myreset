// MyReset のサービスワーカー：リマインダー通知を表示する。
// サーバーからは「通知があるよ」という合図だけが届くので、表示する内容は /api/reminder から取得する。
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
    event.waitUntil((async () => {
        let message = { title: 'MyReset', body: '今日の習慣をチェックしましょう', url: '/' };
        try {
            const response = await fetch('/api/reminder', { credentials: 'include', headers: { 'Accept': 'application/json' } });
            if (response.ok && (response.headers.get('content-type') || '').includes('json')) {
                message = await response.json();
            }
        } catch {
            // 取得できなくても、決まった文面で通知する
        }
        await self.registration.showNotification(message.title, {
            body: message.body,
            icon: '/icons/icon-192.png',
            badge: '/icons/icon-192.png',
            tag: 'myreset-reminder',
            data: { url: message.url || '/' }
        });
    })());
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const url = event.notification.data?.url || '/';
    event.waitUntil((async () => {
        const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        for (const client of windows) {
            if ('focus' in client) {
                await client.navigate(url).catch(() => {});
                return client.focus();
            }
        }
        return self.clients.openWindow(url);
    })());
});
