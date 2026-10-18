var process = { env: {}, versions: {} };
var require = function () {
  return {
    DOMImplementation: function () { this.createDocument = function () { return {}; }; },
    XMLSerializer: function () {},
    DOMParser: function () {},
    install: function () {},
    evaluate: function () { return null; },
  };
};
var MathJax = {
  loader: { paths: { mathjax: arguments[0] }, require: function (file) { load(file); }, load: ['adaptors/liteDOM'] },
  options: { enableAssistiveMml: false },
  svg: { fontCache: 'none' },
  startup: { typeset: false },
};
load(arguments[0] + '/tex-svg.js');

function renderSvg(request) {
  var node = MathJax.tex2svg(request.tex, { display: request.display });
  var adaptor = MathJax.startup.adaptor;
  var svg = adaptor.firstChild(node);
  var exToPx = request.size * 0.5;
  var width = parseFloat(adaptor.getAttribute(svg, 'width')) * exToPx;
  var height = parseFloat(adaptor.getAttribute(svg, 'height')) * exToPx;
  adaptor.setAttribute(svg, 'width', String(width));
  adaptor.setAttribute(svg, 'height', String(height));
  adaptor.removeAttribute(svg, 'style');
  var markup = adaptor.outerHTML(svg).split('currentColor').join(request.color);
  return { svg: markup, w: width, h: height };
}

MathJax.startup.promise.then(function () {
  printErr('ready');
  var line;
  while (typeof (line = readline()) === 'string' && line.length > 0) {
    var response;
    try { response = renderSvg(JSON.parse(line)); } catch (error) { response = { error: String(error) }; }
    printErr(JSON.stringify(response));
  }
});
