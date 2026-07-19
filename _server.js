// BiliClassic PC 测试服务器
// 用法: node _server.js [端口]
var http = require('http'), fs = require('fs'), path = require('path');
var port = parseInt(process.argv[2], 10) || 8080;
var root = __dirname;
var types = {
    '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css',
    '.png': 'image/png', '.gif': 'image/gif', '.ico': 'image/x-icon',
    '.json': 'application/json', '.less': 'text/plain'
};
http.createServer(function(req, res) {
    var file = path.join(root, req.url.split('?')[0]);
    if (file.indexOf(root) !== 0) { res.writeHead(403); res.end(); return; }
    fs.stat(file, function(err, stat) {
        if (err || !stat.isFile()) { res.writeHead(404); res.end('Not Found'); return; }
        var ext = path.extname(file);
        res.writeHead(200, { 'Content-Type': types[ext] || 'application/octet-stream' });
        fs.createReadStream(file).pipe(res);
    });
}).listen(port, function() {
    console.log('测试服务器: http://localhost:' + port + '/debug.html');
});
