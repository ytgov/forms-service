/**
 * I don't believe script gets loaded here, it seems to only load the style/css to the theme.
 * We'll need to move this somewhere else, probably mnove it to another clientlib and call it from page component
 *
 */

var _paq = window._paq = window._paq || [];

_paq.push(['trackPageView']);
_paq.push(['enableLinkTracking']);

(function() {
    var u="https://analytics.gov.yk.ca/";
    _paq.push(['setTrackerUrl', u+'matomo.php']);
    _paq.push(['setSiteId', '4']);
    var d=document, g=d.createElement('script'), s=d.getElementsByTagName('script')[0];
    g.async=true; g.src=u+'matomo.js'; s.parentNode.insertBefore(g,s);
})();
