export function accountValues(data, register) {
  const username = data.username.trim(); const password = data.password;
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(username)) throw new Error("Tên đăng nhập gồm 3–32 chữ, số hoặc dấu gạch dưới.");
  if (password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72) throw new Error("Mật khẩu gồm 8–72 ký tự, tối đa 72 byte UTF-8.");
  const result = {username, password};
  if (register) { if (!data.displayName.trim() || data.displayName.length > 100) throw new Error("Tên hiển thị cần 1–100 ký tự."); result.displayName = data.displayName.trim(); }
  return result;
}
