document.addEventListener("DOMContentLoaded", function () {
    var links = document.querySelectorAll('.yg-submit-feedback-link');
    for (var i=0; i<links.length; i++) {
        (function (link) {
            var feedbackQueryString;
            // Check if an onlineService is already specified
            if (link.href.indexOf("onlineService") !== -1) {
                // If so, just provide the current URL
                feedbackQueryString = "url_referrer" + encodeURIComponent(window.location);
            } else {
                // If not, then also provide the current page title as the onlineService name
                feedbackQueryString = "onlineService=" + encodeURIComponent(document.title) + "&url_referrer=" + encodeURIComponent(window.location);
            }

            // Check if there's an existing query string
            if (link.href.indexOf("?") !== -1) {
                // In this case there's already a query string, so append with & in between
                link.href = link.href + "&" + feedbackQueryString;
            } else {
                // In this case, there isn't a query string yet, so append with a ? in between
                link.href = link.href + "?" + feedbackQueryString;
            }
            console.log("setFeedbackLinkParameters.js: => feedbackQueryString=" + feedbackQueryString)
        })(links[i]);
    }
});
