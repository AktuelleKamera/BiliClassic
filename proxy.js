var http = require('http'), urlMod = require('url'), exec = require('child_process').exec;
var PORT = 18081;

http.createServer(function(req, r) {
    if (req.method === 'OPTIONS') {
        r.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'x-bili-cookie, content-type', 'Access-Control-Max-Age': '86400' });
        r.end(); return;
    }
    var u = urlMod.parse(req.url, true);
    var path = u.pathname || '/';
    var qs = u.search || '';

    if (path === '/proxy/video' && u.query && u.query.url) {
        var src = u.query.url;
        exec('curl -s -k -L --connect-timeout 15 --max-time 180 -H "Referer: https://www.bilibili.com/" -H "User-Agent: Mozilla/5.0" "' + src + '"', { maxBuffer: 500 * 1024 * 1024 }, function(e, o) {
            r.writeHead(200, { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'video/mp4' });
            r.end(o || '');
        });
        return;
    }

    var host = path.indexOf('/x/passport-login/') === 0 ? 'passport.bilibili.com' : 'api.bilibili.com';
    var cookie = req.headers['x-bili-cookie'] || '';
    var cmd = 'curl -s -k --connect-timeout 10 --max-time 30 -D /dev/stderr -H "Referer: https://www.bilibili.com/" -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"';
    if (cookie) cmd += ' -H "Cookie: ' + cookie.replace(/"/g, '\\"') + '"';
    cmd += ' "https://' + host + path + qs + '"';

    exec(cmd, function(e, o, err) {
        var setCookie = '';
        if (err) {
            var lines = err.split('\n');
            for (var i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().indexOf('set-cookie:') === 0) {
                    var val = lines[i].substring(11).trim();
                    setCookie = setCookie ? setCookie + ', ' + val : val;
                }
            }
        }
        if (o && o.charAt(0) === '{' && setCookie) {
            o = o.slice(0, -1) + ',"_cookie":"' + setCookie.replace(/"/g, '\\"') + '"}';
        }
        r.writeHead(200, { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'application/json; charset=utf-8' });
        r.end(o || '{"code":-1,"message":"empty"}');
    });
}).listen(PORT, '127.0.0.1', function() {
    console.log('ready');
});
