// Jump to the wanted page, then tell the app which page is at the top while scrolling.
(function () {
  var marks = Array.prototype.slice.call(document.querySelectorAll('a.pg'));
  function jump() {
    var a = document.getElementById('pg' + want);
    if (a && marks.indexOf(a) > 0) window.scrollTo(0, a.getBoundingClientRect().top + window.pageYOffset - 8);
  }
  function report() {
    var cur = null;
    for (var i = 0; i < marks.length; i++) {
      if (marks[i].getBoundingClientRect().top <= 40) cur = marks[i]; else break;
    }
    if (cur && window.App) App.page(parseInt(cur.id.substring(2), 10));
  }
  var timer = null;
  window.addEventListener('scroll', function () {
    if (timer) return;
    timer = setTimeout(function () { timer = null; report(); }, 250);
  });
  window.addEventListener('load', function () { jump(); setTimeout(jump, 400); });
  jump();
})();
