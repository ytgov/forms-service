function setAccordion(accordionEl, expand) {
  // Get all child panels
  const panels = accordionEl.querySelectorAll('[data-guide-parent-id]');

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
  const expandBtn = e.target.closest('[aria-label="Expand All"]');
  const collapseBtn = e.target.closest('[aria-label="Collapse All"]');
  
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
