/*
 * Cấu hình chung cho frontend, nạp trước mọi script khác.
 *  - API_BASE: địa chỉ API Gateway, tự suy ra theo nơi đang chạy (localhost, Docker, Codespaces).
 *  - Tự gắn JWT vào mọi request gửi tới Gateway (Chương 5).
 *  - Token hết hạn / không hợp lệ (401) thì đưa người dùng về trang đăng nhập.
 */
(function () {
  const loc = window.location;
  let apiOrigin;

  if (loc.hostname.endsWith(".app.github.dev")) {
    // Codespaces: https://<tên>-5500.app.github.dev -> https://<tên>-8088.app.github.dev
    apiOrigin = loc.origin.replace(/-\d+\.app\.github\.dev$/, "-8088.app.github.dev");
  } else if (loc.hostname) {
    apiOrigin = `${loc.protocol}//${loc.hostname}:8088`;
  } else {
    apiOrigin = "http://localhost:8088"; // mở file .html trực tiếp
  }

  window.API_BASE = apiOrigin + "/api";

  const originalFetch = window.fetch.bind(window);

  window.fetch = function (input, init) {
    const url = typeof input === "string" ? input : input.url;
    const options = Object.assign({}, init);

    if (url.startsWith(window.API_BASE)) {
      const token = localStorage.getItem("token");
      const headers = new Headers(options.headers || {});
      if (token && !headers.has("Authorization")) {
        headers.set("Authorization", "Bearer " + token);
      }
      options.headers = headers;
    }

    return originalFetch(input, options).then((res) => {
      const isLogin = url.includes("/auth/login");
      if (res.status === 401 && url.startsWith(window.API_BASE) && !isLogin) {
        const isAdminPage = loc.pathname.includes("admin");
        localStorage.clear();
        alert("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        location.href = isAdminPage ? "admin-login.html" : "index.html";
      }
      return res;
    });
  };
})();
