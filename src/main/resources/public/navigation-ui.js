/* Responsive disclosure for the existing section buttons; routing stays in showView. */
(() => {
  const sidebar = document.querySelector(".sidebar");
  const navigation = document.getElementById("primary-navigation");
  const toggle = document.getElementById("navigation-toggle");
  const current = document.getElementById("navigation-current");
  const buttons = [...navigation.querySelectorAll(".nav-item[data-view]")];
  const compact = window.matchMedia("(max-width: 1023px)");
  let currentLabel = "Обзор";
  let toggleFocused = false;

  function setExpanded(expanded, restoreFocus = false) {
    const open = !compact.matches || Boolean(expanded);
    const containedFocus = navigation.contains(document.activeElement);
    navigation.hidden = !open;
    toggle.setAttribute("aria-expanded", String(open));
    toggle.setAttribute("aria-label", `${open ? "Закрыть" : "Открыть"} меню разделов. Текущий раздел: ${currentLabel}`);
    if (compact.matches && !open && (restoreFocus || containedFocus)) toggle.focus();
  }

  function onView(view) {
    const section = ["project-card", "project-architecture"].includes(view) ? "projects" : view;
    const selected = buttons.find(button => button.dataset.view === section) ?? buttons[0];
    for (const button of buttons) {
      const active = button === selected;
      button.classList.toggle("active", active);
      if (active) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    }
    currentLabel = selected.textContent.trim();
    current.textContent = currentLabel;
    setExpanded(false);
  }

  toggle.addEventListener("click", () => {
    setExpanded(toggle.getAttribute("aria-expanded") !== "true");
  });
  toggle.addEventListener("focus", () => { toggleFocused = true; });
  toggle.addEventListener("blur", () => {
    // On expansion CSS may hide the toggle before the media-change callback runs.
    if (compact.matches) toggleFocused = false;
  });
  sidebar.addEventListener("keydown", event => {
    if (event.key === "Escape" && compact.matches && !navigation.hidden) {
      event.preventDefault();
      setExpanded(false, true);
    }
  });
  document.addEventListener("click", event => {
    if (compact.matches && !navigation.hidden && !sidebar.contains(event.target)) setExpanded(false);
  });
  compact.addEventListener("change", () => {
    const toggleHadFocus = document.activeElement === toggle || toggleFocused;
    setExpanded(false);
    if (!compact.matches) {
      if (toggleHadFocus) buttons.find(button => button.getAttribute("aria-current") === "page")?.focus();
      toggleFocused = false;
    }
  });

  sidebar.dataset.navigationReady = "true";
  window.PMNavigationUI = { onView };
  onView(state.activeView);
})();
