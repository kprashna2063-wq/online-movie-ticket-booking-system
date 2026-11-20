// MovieBook frontend: authentication (register, login, logout, forgot/reset password).
// All real authentication happens in the Spring Boot backend; this file only calls its API.

// If the site is served by the backend (http://localhost:8080) the API is same-origin.
// Otherwise (e.g. VS Code Live Server on :5500) it is called at localhost:8080 with cookies.
// Override by defining window.MOVIEBOOK_API_BASE before this script loads.
const API_BASE = window.MOVIEBOOK_API_BASE ?? (window.location.port === "8080" ? "" : "http://localhost:8080");

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function apiRequest(path, { method = "GET", body } = {}) {
  const options = { method, credentials: "include", headers: { Accept: "application/json" } };
  if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(API_BASE + path, options);
  } catch {
    throw new ApiError(0, "Cannot reach the server. Make sure the backend is running (http://localhost:8080) and open the site from http://localhost:8080.");
  }

  let data = null;
  try { data = await response.json(); } catch { /* empty or non-JSON body */ }

  if (!response.ok) {
    let message = (data && data.message) || "Something went wrong. Please try again.";
    if (data && data.errors) message = Object.values(data.errors).join(" ");
    throw new ApiError(response.status, message);
  }
  return data;
}

// ---------------------------------------------------------------- helpers

function showMessage(text, type = "error") {
  const box = document.querySelector("#form-message");
  if (!box) return;
  box.textContent = text;           // textContent: never inject server text as HTML
  box.className = `form-message ${type}`;
  box.hidden = false;
}

function setBusy(form, busy) {
  const button = form.querySelector("button[type=submit]");
  if (button) button.disabled = busy;
}

// Only allow redirecting back to our own simple page names (prevents open redirects).
function safeNextPage() {
  const next = new URLSearchParams(window.location.search).get("next");
  return next && /^[a-z0-9-]+\.html$/i.test(next) ? next : "index.html";
}

async function getCurrentUser() {
  try {
    return await apiRequest("/api/auth/me");
  } catch {
    return null; // 401 = logged out; network errors are treated the same for the navbar
  }
}

// ----------------------------------------------------------------- navbar

function renderAuthNav(user) {
  const slot = document.querySelector("#auth-nav");
  if (!slot) return;
  slot.replaceChildren();

  if (!user) {
    const login = document.createElement("a");
    login.href = "login.html";
    login.textContent = "Login";
    const register = document.createElement("a");
    register.href = "register.html";
    register.className = "nav-button";
    register.textContent = "Register";
    slot.append(login, register);
    return;
  }

  const greeting = document.createElement("span");
  greeting.className = "nav-user";
  greeting.textContent = `Hi, ${user.fullName.split(" ")[0]}`;

  const logoutButton = document.createElement("button");
  logoutButton.type = "button";
  logoutButton.id = "logout-button";
  logoutButton.className = "nav-button";
  logoutButton.textContent = "Logout";
  logoutButton.addEventListener("click", logout);

  slot.append(greeting, logoutButton);
}

async function logout() {
  const button = document.querySelector("#logout-button");
  if (button) button.disabled = true;
  try {
    await apiRequest("/api/auth/logout", { method: "POST" });
    window.location.href = "login.html?loggedout=1";
  } catch (error) {
    if (button) button.disabled = false;
    alert(`Logout failed: ${error.message}`);
  }
}

async function initAuth() {
  const user = await getCurrentUser();
  renderAuthNav(user);

  if (document.body.hasAttribute("data-requires-auth")) {
    if (!user) {
      const page = window.location.pathname.split("/").pop() || "index.html";
      window.location.replace(`login.html?next=${encodeURIComponent(page)}`);
      return;
    }
    document.body.classList.add("auth-ready");
  }
}

// ------------------------------------------------------------------ forms

function initRegisterForm() {
  const form = document.querySelector("#register-form");
  if (!form) return;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    if (data.get("password") !== data.get("confirmPassword")) {
      showMessage("Passwords do not match.");
      return;
    }
    setBusy(form, true);
    try {
      await apiRequest("/api/auth/register", {
        method: "POST",
        body: {
          fullName: data.get("fullName"),
          email: data.get("email"),
          phone: data.get("phone"),
          password: data.get("password"),
        },
      });
      window.location.href = "login.html?registered=1";
    } catch (error) {
      showMessage(error.message);
      setBusy(form, false);
    }
  });
}

function initLoginForm() {
  const form = document.querySelector("#login-form");
  if (!form) return;

  const params = new URLSearchParams(window.location.search);
  if (params.has("registered")) showMessage("Account created. Please log in.", "success");
  else if (params.has("loggedout")) showMessage("You have been logged out.", "info");
  else if (params.has("reset")) showMessage("Password reset. Log in with your new password.", "success");
  else if (params.has("next")) showMessage("Please log in to continue.", "info");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    setBusy(form, true);
    try {
      await apiRequest("/api/auth/login", {
        method: "POST",
        body: { email: data.get("email"), password: data.get("password") },
      });
      window.location.href = safeNextPage();
    } catch (error) {
      showMessage(error.message);
      setBusy(form, false);
    }
  });
}

function initForgotPasswordForm() {
  const form = document.querySelector("#forgot-form");
  if (!form) return;

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    setBusy(form, true);
    try {
      const result = await apiRequest("/api/auth/forgot-password", {
        method: "POST",
        body: { email: data.get("email") },
      });
      showMessage(result.message, "success");
      form.reset();
    } catch (error) {
      showMessage(error.message);
    } finally {
      setBusy(form, false);
    }
  });
}

function initResetPasswordForm() {
  const form = document.querySelector("#reset-form");
  if (!form) return;

  const token = new URLSearchParams(window.location.search).get("token");
  // Remove the token from the address bar / history as soon as it has been read.
  window.history.replaceState(null, "", window.location.pathname);

  if (!token) {
    form.hidden = true;
    showMessage("This reset link is missing its token. Please request a new one from the Forgot password page.");
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    if (data.get("newPassword") !== data.get("confirmPassword")) {
      showMessage("Passwords do not match.");
      return;
    }
    setBusy(form, true);
    try {
      await apiRequest("/api/auth/reset-password", {
        method: "POST",
        body: { token, newPassword: data.get("newPassword") },
      });
      window.location.href = "login.html?reset=1";
    } catch (error) {
      showMessage(error.message);
      setBusy(form, false);
    }
  });
}

// ------------------------------------------------------------------- init

document.addEventListener("DOMContentLoaded", () => {
  initAuth();
  initRegisterForm();
  initLoginForm();
  initForgotPasswordForm();
  initResetPasswordForm();
});

// Back/forward cache: after logging out, pressing Back must not show a logged-in page.
window.addEventListener("pageshow", (event) => {
  if (event.persisted) initAuth();
});
