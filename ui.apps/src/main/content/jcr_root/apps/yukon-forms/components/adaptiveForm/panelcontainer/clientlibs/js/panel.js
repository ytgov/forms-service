function setAllAccordions(expand) {
  document.querySelectorAll('.accordion').forEach(accordion => {
    console.debug('in function, accordion:', accordion)
    accordion.querySelectorAll('[aria-expanded]').forEach(btn => {
      const isExpanded = btn.getAttribute('aria-expanded') === 'true';
      if (expand && !isExpanded) btn.click();
      if (!expand && isExpanded) btn.click();
    });
  });
}

// TODO: fix click mechanism
document.addEventListener('click', (e) => {
  if (e.target.closest('[name="expandAllBtn"]')) {
    console.debug('set to true');
    setAllAccordions(true);
  }
  if (e.target.closest('[name="collapseAllBtn"]')) {
    console.debug('set to false');
    setAllAccordions(false);
  }
})
