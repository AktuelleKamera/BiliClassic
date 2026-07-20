var http = require('http'), urlMod = require('url'), exec = require('child_process').exec;
var PORT = 18081;

http.createServer(function(req, r) {
    function cors(o) { o['Access-Control-Allow-Origin'] = req.headers['origin'] || '*'; return o; }
    if (req.method === 'OPTIONS') {
        r.writeHead(204, cors({ 'Access-Control-Allow-Headers': 'x-bili-cookie, content-type', 'Access-Control-Max-Age': '86400' }));
        r.end(); return;
    }
    var u = urlMod.parse(req.url, true);
    var path = u.pathname || '/';
    var qs = u.search || '';

    if (path === '/proxy/video' && u.query && u.query.url) {
        var src = u.query.url;
        exec('curl -s -k -L --connect-timeout 15 --max-time 180 -H "Referer: https://www.bilibili.com/" -H "User-Agent: Mozilla/5.0" "' + src + '"', { maxBuffer: 500 * 1024 * 1024 }, function(e, o) {
            r.writeHead(200, cors({ 'Content-Type': 'video/mp4' }));
            r.end(o || '');
        });
        return;
    }

    var host = path.indexOf('/x/passport-login/') === 0 ? 'passport.bilibili.com' : 'api.bilibili.com';
    var cookie = req.headers['x-bili-cookie'] || '';
    var method = req.method || 'GET';
    var cmd = 'curl -s -k --connect-timeout 10 --max-time 30 -D /dev/stderr -H "Referer: https://www.bilibili.com/" -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"';
    if (cookie) cmd += ' -H "Cookie: ' + cookie.replace(/"/g, '\\"') + '"';

    if (method === 'POST') {
        var body = '';
        req.on('data', function(d) { body += d; });
        req.on('end', function() {
            cmd += ' -X POST --data "' + body.replace(/"/g, '\\"') + '"';
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
                r.writeHead(200, cors({ 'Content-Type': 'application/json; charset=utf-8' }));
                r.end(o || '{"code":-1,"message":"empty"}');
            });
        });
    } else {
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
        r.writeHead(200, cors({ 'Content-Type': 'application/json; charset=utf-8' }));
        r.end(o || '{"code":-1,"message":"empty"}');
    });
    }
}).listen(PORT, '0.0.0.0', function() {
    console.log('ready');
});
