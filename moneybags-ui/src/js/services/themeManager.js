define([], function () {
  'use strict';

  var STORAGE_KEY = 'moneybags-theme';
  var THEMES = ['light', 'dark'];

  function isValidTheme(theme) {
    return THEMES.indexOf(theme) !== -1;
  }

  function getStoredTheme() {
    try {
      var storedTheme = localStorage.getItem(STORAGE_KEY);
      return isValidTheme(storedTheme) ? storedTheme : null;
    } catch (error) {
      return null;
    }
  }

  function getSystemTheme() {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  function current() {
    return getStoredTheme() || getSystemTheme();
  }

  function apply(theme, persist) {
    var selectedTheme = isValidTheme(theme) ? theme : 'light';
    var root = document.documentElement;

    root.dataset.mbTheme = selectedTheme;
    root.style.colorScheme = selectedTheme;

    if (persist !== false) {
      try {
        localStorage.setItem(STORAGE_KEY, selectedTheme);
      } catch (error) {
        // Theme switching still works when storage is unavailable.
      }
    }

    return selectedTheme;
  }

  function restore() {
    return apply(current(), false);
  }

  function toggle() {
    return apply(current() === 'dark' ? 'light' : 'dark');
  }

  return {
    apply: apply,
    current: current,
    restore: restore,
    toggle: toggle
  };
});
