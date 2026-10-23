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
})();
