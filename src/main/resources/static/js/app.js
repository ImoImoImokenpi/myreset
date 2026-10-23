// MyReset の画面操作。
// data-instant を付けたフォーム（達成チェック・応援・公開切り替え）は、ページを読み込み直さずに
// 押した瞬間に見た目を変え、裏でサーバーに送って結果の画面に差し替える。
(() => {
    let latest = 0;

    /** 押した瞬間の見た目の変化（サーバーの応答を待たない）。 */
    function applyOptimistic(form) {
        switch (form.dataset.instant) {
            case 'check':
                form.closest('li')?.classList.toggle('done');
                break;
            case 'cheer': {
                const button = form.querySelector('.cheer');
                button?.classList.toggle('on');
                break;
            }
            case 'amount': {
                // 量の「＋」「−」：数字とバーをその場で更新する
                const li = form.closest('li');
                const delta = Number(form.elements.delta.value);
                const target = Number(li.dataset.target);
                const amount = Math.max(0, Number(li.dataset.amount) + delta);
                li.dataset.amount = String(amount);
                li.querySelector('.amount-text').textContent = `${amount} / ${target}${li.dataset.unit}`;
                li.querySelector('.amount-bar > div').style.width = `${Math.min(100, amount * 100 / target)}%`;
                li.classList.toggle('done', amount >= target);
                break;
            }
            case 'share':
                form.querySelector('button')?.classList.add('pending');
                break;
        }
    }

    /** サーバーから返ってきた画面で、本文とナビ（申請バッジなど）を置き換える。 */
    function swapPage(html) {
        const doc = new DOMParser().parseFromString(html, 'text/html');
        const newMain = doc.querySelector('main');
        const newNav = doc.querySelector('.site-nav');
        if (!newMain) {
            return false;
        }
        document.querySelector('main').replaceWith(newMain);
        const nav = document.querySelector('.site-nav');
        if (nav && newNav) {
            nav.replaceWith(newNav);
        }
        return true;
    }

    document.addEventListener('submit', async (event) => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.dataset.instant) {
            return;
        }
        event.preventDefault();
        applyOptimistic(form);

        const id = ++latest;
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                // multipart だと CSRF トークンが読まれないので、通常のフォームと同じ形式で送る
                body: new URLSearchParams(new FormData(form)),
                headers: { 'Accept': 'text/html' },
                credentials: 'same-origin'
            });
            if (!response.ok) {
                throw new Error(String(response.status));
            }
            const html = await response.text();
            // 連打したときは、最後の操作の結果だけを反映する
            if (id === latest && !swapPage(html)) {
                location.reload();
            }
        } catch {
            // ログイン切れや通信エラーのときは、正しい状態を表示し直す
            location.reload();
        }
    });

    // 習慣の名前変更フォームの開閉
    document.addEventListener('click', (event) => {
        const toggle = event.target.closest('[data-rename-toggle]');
        if (!toggle) {
            return;
        }
        const li = toggle.closest('li');
        const opening = !li.classList.contains('renaming');
        closeRenameForms();
        if (opening) {
            li.classList.add('renaming');
            const input = li.querySelector('.rename-form input[name=name]');
            input.focus();
            input.select();
        }
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            closeRenameForms();
        }
    });

    /** 開いている名前変更フォームを閉じ、入力を元の名前に戻す。 */
    function closeRenameForms() {
        document.querySelectorAll('li.renaming').forEach(li => {
            li.classList.remove('renaming');
            const input = li.querySelector('.rename-form input[name=name]');
            input.value = input.defaultValue;
        });
    }

    // 招待リンクのコピー・共有（本文が差し替わっても動くよう document で受ける）
    document.addEventListener('click', async (event) => {
        const copy = event.target.closest('#copy-invite');
        const share = event.target.closest('#share-invite');
        const input = document.getElementById('invite-url');
        if (!input) {
            return;
        }
        if (copy) {
            try {
                await navigator.clipboard.writeText(input.value);
            } catch {
                input.select();
                document.execCommand('copy');
            }
            const label = copy.querySelector('span');
            label.textContent = 'コピーしました';
            setTimeout(() => label.textContent = 'コピー', 2000);
        }
        if (share && navigator.share) {
            navigator.share({
                title: 'MyReset',
                text: 'MyReset で一緒に習慣を続けよう！',
                url: input.value
            }).catch(() => {});
        }
    });

    function showShareButton() {
        const share = document.getElementById('share-invite');
        if (share && navigator.share) {
            share.hidden = false;
        }
    }

    showShareButton();
    new MutationObserver(showShareButton).observe(document.body, { childList: true });

    // ---- リマインダー通知 ----

    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => {});
    }

    function keyToBytes(base64url) {
        const padding = '='.repeat((4 - base64url.length % 4) % 4);
        const raw = atob((base64url + padding).replace(/-/g, '+').replace(/_/g, '/'));
        return Uint8Array.from(raw, c => c.charCodeAt(0));
    }

    async function postForm(url, params) {
        const csrf = document.querySelector('#push-form input[name="_csrf"]');
        const body = new URLSearchParams(params);
        if (csrf) {
            body.append('_csrf', csrf.value);
        }
        const response = await fetch(url, { method: 'POST', body, credentials: 'same-origin' });
        if (!response.ok) {
            throw new Error(String(response.status));
        }
    }

    async function setupPush() {
        const box = document.getElementById('push-box');
        if (!box) {
            return;
        }
        const status = document.getElementById('push-status');
        const enable = document.getElementById('push-enable');
        const disable = document.getElementById('push-disable');
        const iosHint = document.getElementById('push-ios-hint');
        const isIos = /iPhone|iPad|iPod/.test(navigator.userAgent)
            || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
        const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;

        if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
            status.textContent = 'このブラウザでは通知を受け取れません。';
            iosHint.hidden = !isIos || standalone;
            return;
        }
        const registration = await navigator.serviceWorker.ready;
        let subscription = await registration.pushManager.getSubscription();

        const render = () => {
            if (Notification.permission === 'denied') {
                status.textContent = '通知がブロックされています。ブラウザやスマホの設定で、このサイトの通知を許可してください。';
            } else if (subscription) {
                status.textContent = 'この端末で通知を受け取ります。';
            } else {
                status.textContent = 'この端末ではまだ通知を受け取っていません。';
            }
            enable.hidden = !!subscription || Notification.permission === 'denied';
            disable.hidden = !subscription;
        };
        render();

        enable.addEventListener('click', async () => {
            enable.disabled = true;
            try {
                if (await Notification.requestPermission() !== 'granted') {
                    render();
                    return;
                }
                subscription = await registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: keyToBytes(box.dataset.key)
                });
                await postForm('/api/push/subscribe', { endpoint: subscription.endpoint });
                location.reload();
            } catch (e) {
                status.textContent = '通知の登録に失敗しました。もう一度お試しください。';
            } finally {
                enable.disabled = false;
            }
        });

        disable.addEventListener('click', async () => {
            if (!subscription) {
                return;
            }
            const endpoint = subscription.endpoint;
            await subscription.unsubscribe().catch(() => {});
            await postForm('/api/push/unsubscribe', { endpoint }).catch(() => {});
            location.reload();
        });
    }

    setupPush().catch(() => {});
})();
