function setAccordion(accordionEl, expand) {
  // Get all child panels
  var panels = accordionEl.querySelectorAll(':scope > [data-guide-parent-id]');

  panels.forEach(function(panel) {
    var btn = panel.querySelector('[aria-expanded]');
    var content = panel.querySelector('.afAccordionPanel');

    if (expand) {
      panel.classList.add('active');
      if (btn) {
        btn.setAttribute('aria-expanded', 'true');
        btn.setAttribute('aria-pressed', 'true');
      }
      if (content) content.style.display = '';
    } 
    else {
      panel.classList.remove('active');
      if (btn) {
        btn.setAttribute('aria-expanded', 'false');
        btn.setAttribute('aria-pressed', 'false');
      }
      if (content) content.style.display = 'none';
    }
  });
}

document.addEventListener('click', function(e) {
  var expandBtn = e.target.closest('.expandAllPanelsButton');
  var collapseBtn = e.target.closest('.collapseAllPanelsButton');
  
  var clicked = expandBtn || collapseBtn;
  if (!clicked) return;

  var expand = !!expandBtn;

  // Walk up to the outermost guide-item wrapper
  var buttonWrapper = clicked.closest('[data-guide-parent-id]');
  if (!buttonWrapper) return;

  // The accordion is in a sibling div, find the next sibling that contains .accordion-navigators
  var sibling = buttonWrapper.nextElementSibling;
  while (sibling) {
    var accordion = sibling.querySelector('.accordion-navigators');
    if (accordion) {
      setAccordion(accordion, expand);
      return;
    }
    sibling = sibling.nextElementSibling;
  }
});

// Replace AEM's built-in panel header functionality to work with our expand/collapse all
document.addEventListener('click', function(e) {
  // Allow the remove button to work normally
  if (e.target.closest('[data-guide-addremove="remove"]')) return;

  var toggle = e.target.closest('[data-guide-toggle="accordion-tab"]');
  if (!toggle) return;

  // Find the .accordion-navigators this toggle belongs to
  var accordionNav = toggle.closest('.accordion-navigators');
  if (!accordionNav) return;

  // Walk up to the outermost guide-item wrapper that contains this accordion
  var accordionWrapper = accordionNav.closest('[data-guide-parent-id]');
  if (!accordionWrapper) return;

  // Check if a sibling expand/collapse button exists
  var hasManagedButton = accordionWrapper.parentElement ? accordionWrapper.parentElement.querySelector(
    '.expandAllPanelsButton, ' +
    '.collapseAllPanelsButton'
  ) : null;

  // If no expand/collapse button nearby, var AEM handle it normally
  if (!hasManagedButton) return;

  // Otherwise, stop AEM's listener from firing
  e.stopPropagation();
  e.preventDefault();

  var panel = toggle.closest('[data-guide-parent-id]');
  if (!panel) return;

  var btn = panel.querySelector('[aria-expanded]');
  var content = panel.querySelector('.afAccordionPanel');
  var isExpanded = btn && btn.getAttribute('aria-expanded') === 'true';

  if (isExpanded) {
    panel.classList.remove('active');
    if (btn) {
      btn.setAttribute('aria-expanded', 'false');
      btn.setAttribute('aria-pressed', 'false');
    }
    if (content) content.style.display = 'none';
  } else {
    panel.classList.add('active');
    if (btn) {
      btn.setAttribute('aria-expanded', 'true');
      btn.setAttribute('aria-pressed', 'true');
    }
    if (content) content.style.display = '';
  }
}, true);