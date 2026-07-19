// BiliClassic PC 测试服务器（文件服务 + API 代理）
var http = require('http'), https = require('https'), fs = require('fs'), path = require('path'), urlMod = require('url');
var port = parseInt(process.argv[2], 10) || 8080;
var root = __dirname;
var types = {
    '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css',
    '.png': 'image/png', '.gif': 'image/gif', '.ico': 'image/x-icon',
    '.json': 'application/json', '.webp': 'image/webp', '.jpg': 'image/jpeg'
};

http.createServer(function(req, res) {
    var u = urlMod.parse(req.url, true);
    var pathname = u.pathname;

    // API 代理：转发到 Bilibili
    if (pathname.indexOf('/x/') === 0 || pathname.indexOf('/bapis/') === 0 || pathname.indexOf('/pgc/') === 0) {
        var host = pathname.indexOf('/x/passport-login/') === 0 ? 'passport.bilibili.com' : 'api.bilibili.com';
        var opts = { hostname: host, port: 443, path: pathname + (u.search || ''), method: req.method, headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Accept': 'application/json, text/plain, */*',
            'Referer': 'https://www.bilibili.com/',
            'Origin': 'https://www.bilibili.com'
        }};
        var c = req.headers['x-bili-cookie'];
        if (c) opts.headers['Cookie'] = c;
        var proxyReq = https.request(opts, function(proxyRes) {
            var body = '';
            proxyRes.on('data', function(d) { body += d; });
            proxyRes.on('end', function() {
                var setCookie = proxyRes.headers['set-cookie'];
                var jsonBody = body;
                if (setCookie && body.charAt(0) === '{') {
                    var cookieStr = Array.isArray(setCookie) ? setCookie.join(', ') : setCookie;
                    jsonBody = body.slice(0, -1) + ',"_cookie":"' + cookieStr.replace(/"/g, '\\"') + '"}';
                }
                res.writeHead(proxyRes.statusCode || 200, {
                    'Access-Control-Allow-Origin': '*',
                    'Access-Control-Allow-Headers': 'x-bili-cookie, content-type',
                    'Content-Type': 'application/json; charset=utf-8'
                });
                res.end(jsonBody);
            });
        });
        proxyReq.on('error', function() { res.writeHead(502); res.end('{"code":-1,"message":"proxy error"}'); });
        proxyReq.end();
        return;
    }

    // 文件服务
    if (req.method === 'OPTIONS') {
        res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-bili-cookie, content-type' });
        res.end();
        return;
    }
    var file = path.join(root, pathname);
    if (file.indexOf(root) !== 0) { res.writeHead(403); res.end(); return; }
    fs.stat(file, function(err, stat) {
        if (err || !stat.isFile()) { res.writeHead(404); res.end('Not Found'); return; }
        var ext = path.extname(file);
        res.writeHead(200, { 'Content-Type': types[ext] || 'application/octet-stream', 'Access-Control-Allow-Origin': '*' });
        fs.createReadStream(file).pipe(res);
    });
}).listen(port, function() {
    console.log('BiliClassic 测试服务器: http://localhost:' + port + '/debug.html');
});
