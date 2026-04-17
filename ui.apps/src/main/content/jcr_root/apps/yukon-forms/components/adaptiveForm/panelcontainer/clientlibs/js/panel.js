function setAccordion(accordionEl, expand) {
  // Get all child panels
  const panels = accordionEl.querySelectorAll(':scope > [data-guide-parent-id]');

  panels.forEach(panel => {
    const btn = panel.querySelector('[aria-expanded]');
    const content = panel.querySelector('.afAccordionPanel');

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

document.addEventListener('click', (e) => {
  const expandBtn = e.target.closest('[aria-label="Expand All"]') || e.target.closest('[aria-label="Tout afficher"]');
  const collapseBtn = e.target.closest('[aria-label="Collapse All"]') || e.target.closest('[aria-label="Masquer"]');
  
  const clicked = expandBtn || collapseBtn;
  if (!clicked) return;

  const expand = !!expandBtn;

  // Walk up to the outermost guide-item wrapper
  const buttonWrapper = clicked.closest('[data-guide-parent-id]');
  if (!buttonWrapper) return;

  // The accordion is in a sibling div, find the next sibling that contains .accordion-navigators
  let sibling = buttonWrapper.nextElementSibling;
  while (sibling) {
    const accordion = sibling.querySelector('.accordion-navigators');
    if (accordion) {
      setAccordion(accordion, expand);
      return;
    }
    sibling = sibling.nextElementSibling;
  }
});

// Replace AEM's built-in panel header functionality to work with our expand/collapse all
document.addEventListener('click', (e) => {
  const toggle = e.target.closest('[data-guide-toggle="accordion-tab"]');
  if (!toggle) return;

  // Find the .accordion-navigators this toggle belongs to
  const accordionNav = toggle.closest('.accordion-navigators');
  if (!accordionNav) return;

  // Walk up to the outermost guide-item wrapper that contains this accordion
  const accordionWrapper = accordionNav.closest('[data-guide-parent-id]');
  if (!accordionWrapper) return;

  // Check if a sibling expand/collapse button exists
  const hasManagedButton = accordionWrapper.parentElement?.querySelector(
    '[aria-label="Expand All"], [aria-label="Collapse All"], ' +
    '[aria-label="Tout afficher"], [aria-label="Masquer"]'
  );

  // If no expand/collapse button nearby, let AEM handle it normally
  if (!hasManagedButton) return;

  // Otherwise, stop AEM's listener from firing
  e.stopPropagation();
  e.preventDefault();

  const panel = toggle.closest('[data-guide-parent-id]');
  if (!panel) return;

  const btn = panel.querySelector('[aria-expanded]');
  const content = panel.querySelector('.afAccordionPanel');
  const isExpanded = btn?.getAttribute('aria-expanded') === 'true';

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