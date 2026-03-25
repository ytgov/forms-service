document.addEventListener("DOMContentLoaded", function () {
    var nopasteElements = document.getElementsByClassName('nopasteallowed');

    for (var i = 0; i < nopasteElements.length; i++) {
        var input = nopasteElements[i].querySelector('input');
        if (input) {
            input.addEventListener('paste', function(e) {
                e.preventDefault();
            });
        }
    }
});

