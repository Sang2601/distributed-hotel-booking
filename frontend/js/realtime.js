/*
 * Chương 4 – Thông báo thời gian thực qua WebSocket (STOMP).
 * Kết nối: ws://<gateway>/ws?access_token=<JWT>  (Gateway kiểm tra token, Chương 5)
 * Mất kết nối (ví dụ instance booking-service đang nối bị tắt) thì tự nối lại sau 3 giây;
 * Gateway sẽ chuyển sang instance còn sống (Chương 8).
 */
(function () {
  function statusLabel(status) {
    return {
      PENDING_PAYMENT: "chờ thanh toán",
      PAYING: "đang xử lý thanh toán",
      CONFIRMED: "đã thanh toán ✅",
      PAYMENT_FAILED: "thanh toán thất bại ❌",
      CANCELLED: "đã huỷ"
    }[status] || status;
  }

  function ensureBadge() {
    let badge = document.getElementById("liveBadge");
    if (!badge) {
      badge = document.createElement("div");
      badge.id = "liveBadge";
      badge.style.cssText =
        "position:fixed;left:16px;bottom:16px;z-index:9999;padding:6px 12px;border-radius:999px;" +
        "font:600 12px system-ui,sans-serif;color:#fff;background:#9e9e9e;box-shadow:0 2px 8px rgba(0,0,0,.2)";
      document.body.appendChild(badge);
    }
    return badge;
  }

  function setLive(isLive) {
    const badge = ensureBadge();
    badge.textContent = isLive ? "● Cập nhật trực tiếp" : "○ Đang kết nối lại…";
    badge.style.background = isLive ? "#2e7d32" : "#9e9e9e";
  }

  window.showToast = function (text) {
    const toast = document.createElement("div");
    toast.textContent = text;
    toast.style.cssText =
      "position:fixed;right:16px;bottom:16px;z-index:9999;max-width:min(360px,calc(100vw - 32px));" +
      "padding:12px 16px;border-radius:10px;background:#1e293b;color:#fff;" +
      "font:14px system-ui,sans-serif;box-shadow:0 4px 16px rgba(0,0,0,.25);transition:opacity .4s";
    document.body.appendChild(toast);
    setTimeout(() => { toast.style.opacity = "0"; }, 4000);
    setTimeout(() => toast.remove(), 4500);
  };

  window.describeBookingUpdate = function (u) {
    return `Booking #${u.id} (phòng ${u.roomId}): ${statusLabel(u.status)}`;
  };

  /** Đăng ký nhận thông báo trên một kênh STOMP. */
  window.subscribeRealtime = function (topic, onMessage) {
    const token = localStorage.getItem("token");
    if (!token) return;
    if (!window.StompJs) {
      console.warn("Không tải được thư viện STOMP, bỏ qua cập nhật trực tiếp");
      return;
    }

    const wsBase = window.API_BASE.replace(/^http/, "ws").replace(/\/api$/, "");
    const client = new StompJs.Client({
      brokerURL: `${wsBase}/ws?access_token=${encodeURIComponent(token)}`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });

    client.onConnect = () => {
      setLive(true);
      client.subscribe(topic, (message) => onMessage(JSON.parse(message.body)));
    };
    client.onWebSocketClose = () => setLive(false);
    client.onStompError = (frame) => console.warn("STOMP lỗi:", frame.headers.message);

    setLive(false);
    client.activate();
  };
})();
