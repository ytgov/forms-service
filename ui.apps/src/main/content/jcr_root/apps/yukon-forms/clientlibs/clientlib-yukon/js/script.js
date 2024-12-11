/**
 * Load Custom Matomo Script
 * @name loadCustomMatomoScript Load Custom Matomo Script
 */

var _paq = window._paq = window._paq || [];

_paq.push(['trackPageView']);

_paq.push(['enableLinkTracking']);

function loadCustomMatomoScript(siteId) {
    var u='https://analytics.gov.yk.ca/';

    _paq.push(['setTrackerUrl', u+'matomo.php']);

    _paq.push(['setSiteId', siteId]);

    var d=document;

    var g=d.createElement('script');

    var s=d.getElementsByTagName('script')[0];

    g.async=true;

    g.src=u+'matomo.js';

    s.parentNode.insertBefore(g,s);
}
