// BiliClassic API Proxy - forwards requests to Bilibili via curl
var http = require('http'), https = require('https'), urlMod = require('url'), exec = require('child_process').exec;
var PORT = 18081;
http.createServer(function(req, r) {
    var u = urlMod.parse(req.url, true);
    var path = u.pathname || '/';
    var qs = u.search || '';

    // 视频代理：用 curl 流式转发，自动带 Referer 头
    if (path === '/proxy/video' && u.query && u.query.url) {
        var src = u.query.url;
        var curl = require('child_process').spawn('curl', [
            '-s', '-k', '-L', '--connect-timeout', '15',
            '-H', 'Referer: https://www.bilibili.com/',
            '-H', 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            src
        ]);
        r.writeHead(200, {
            'Access-Control-Allow-Origin': '*',
            'Content-Type': 'video/mp4'
        });
        curl.stdout.pipe(r);
        curl.on('error', function() { try { r.end(); } catch(e) {} });
        curl.on('close', function() { try { r.end(); } catch(e) {} });
        return;
    }

    r.writeHead(200, {
        'Access-Control-Allow-Origin': '*',
        'Content-Type': 'application/json; charset=utf-8'
    });
    var host = path.indexOf('/x/passport-login/') === 0 ? 'passport.bilibili.com' : 'api.bilibili.com';
    var cmd = 'curl -s -k --connect-timeout 10 "https://' + host + path + qs + '"';
    exec(cmd, function(e, o) {
        r.end(o || '{"code":-1,"message":"' + (e ? e.message : 'empty') + '"}');
    });
}).listen(PORT, '127.0.0.1', function() {
    console.log('BiliClassic proxy ready on port ' + PORT);
});
