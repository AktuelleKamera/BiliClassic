// MD5 - 纯 JS 实现 (webtoolkit.info)
var MD5 = function(s) {
    function add32(a,b){return a+b&4294967295}
    function F(x,y,z){return x&y|~x&z}
    function G(x,y,z){return x&z|y&~z}
    function H(x,y,z){return x^y^z}
    function I(x,y,z){return y^(x|~z)}
    function FF(a,b,c,d,x,s,t){a=add32(add32(a,F(b,c,d)),add32(x,t));return add32(a<<s|a>>>32-s,b)}
    function GG(a,b,c,d,x,s,t){a=add32(add32(a,G(b,c,d)),add32(x,t));return add32(a<<s|a>>>32-s,b)}
    function HH(a,b,c,d,x,s,t){a=add32(add32(a,H(b,c,d)),add32(x,t));return add32(a<<s|a>>>32-s,b)}
    function II(a,b,c,d,x,s,t){a=add32(add32(a,I(b,c,d)),add32(x,t));return add32(a<<s|a>>>32-s,b)}
    var n=s.length*8,blk=(n+8>>6)+1,w=Array(blk<<4);
    for(var i=0;i<blk<<4;i++)w[i]=0;
    for(var i=0;i<s.length;i++)w[i>>2]|=(s.charCodeAt(i)&255)<<((i%4)*8);
    w[n>>5]|=128<<(n%32);
    w[((n+64>>>9<<4)+14)]=n;
    var a=1732584193,b=-271733879,c=-1732584194,d=271733878;
    for(var i=0;i<w.length;i+=16){
        var oa=a,ob=b,oc=c,od=d,x=w.slice(i,i+16);
        a=FF(a,b,c,d,x[0],7,-680876936);d=FF(d,a,b,c,x[1],12,-389564586);c=FF(c,d,a,b,x[2],17,606105819);b=FF(b,c,d,a,x[3],22,-1044525330);
        a=FF(a,b,c,d,x[4],7,-176418897);d=FF(d,a,b,c,x[5],12,1200080426);c=FF(c,d,a,b,x[6],17,-1473231341);b=FF(b,c,d,a,x[7],22,-45705983);
        a=FF(a,b,c,d,x[8],7,1770035416);d=FF(d,a,b,c,x[9],12,-1958414417);c=FF(c,d,a,b,x[10],17,-42063);b=FF(b,c,d,a,x[11],22,-1990404162);
        a=FF(a,b,c,d,x[12],7,1804603682);d=FF(d,a,b,c,x[13],12,-40341101);c=FF(c,d,a,b,x[14],17,-1502002290);b=FF(b,c,d,a,x[15],22,1236535329);
        a=GG(a,b,c,d,x[1],5,-165796510);d=GG(d,a,b,c,x[6],9,-1069501632);c=GG(c,d,a,b,x[11],14,643717713);b=GG(b,c,d,a,x[0],20,-373897302);
        a=GG(a,b,c,d,x[5],5,-701558691);d=GG(d,a,b,c,x[10],9,38016083);c=GG(c,d,a,b,x[15],14,-660478335);b=GG(b,c,d,a,x[4],20,-405537848);
        a=GG(a,b,c,d,x[9],5,568446438);d=GG(d,a,b,c,x[14],9,-1019803690);c=GG(c,d,a,b,x[3],14,-187363961);b=GG(b,c,d,a,x[8],20,1163531501);
        a=GG(a,b,c,d,x[13],5,-1444681467);d=GG(d,a,b,c,x[2],9,-51403784);c=GG(c,d,a,b,x[7],14,1735328473);b=GG(b,c,d,a,x[12],20,-1926607734);
        a=HH(a,b,c,d,x[5],4,-378558);d=HH(d,a,b,c,x[8],11,-2022574463);c=HH(c,d,a,b,x[11],16,1839030562);b=HH(b,c,d,a,x[14],23,-35309556);
        a=HH(a,b,c,d,x[1],4,-1530992060);d=HH(d,a,b,c,x[4],11,1272893353);c=HH(c,d,a,b,x[7],16,-155497632);b=HH(b,c,d,a,x[10],23,-1094730640);
        a=HH(a,b,c,d,x[13],4,681279174);d=HH(d,a,b,c,x[0],11,-358537222);c=HH(c,d,a,b,x[3],16,-722521979);b=HH(b,c,d,a,x[6],23,76029189);
        a=HH(a,b,c,d,x[9],4,-640364487);d=HH(d,a,b,c,x[12],11,-421815835);c=HH(c,d,a,b,x[15],16,530742520);b=HH(b,c,d,a,x[2],23,-995338651);
        a=II(a,b,c,d,x[0],6,-198630844);d=II(d,a,b,c,x[7],10,1126891415);c=II(c,d,a,b,x[14],15,-1416354905);b=II(b,c,d,a,x[5],21,-57434055);
        a=II(a,b,c,d,x[12],6,1700485571);d=II(d,a,b,c,x[3],10,-1894986606);c=II(c,d,a,b,x[10],15,-1051523);b=II(b,c,d,a,x[1],21,-2054922799);
        a=II(a,b,c,d,x[8],6,1873313359);d=II(d,a,b,c,x[15],10,-30611744);c=II(c,d,a,b,x[6],15,-1560198380);b=II(b,c,d,a,x[13],21,1309151649);
        a=II(a,b,c,d,x[4],6,-145523070);d=II(d,a,b,c,x[11],10,-1120210379);c=II(c,d,a,b,x[2],15,718787259);b=II(b,c,d,a,x[9],21,-343485551);
        a=add32(a,oa);b=add32(b,ob);c=add32(c,oc);d=add32(d,od);
    }
    var hex='0123456789abcdef',out='';
    function wr(v){for(var i=0;i<4;i++){var b=(v>>(i*8))&255;out+=hex[b>>4]+hex[b&15];}}
    wr(a);wr(b);wr(c);wr(d);
    return out;
};

var CookieStore = {
    _cookies: {}, _loaded: !1,
    _load: function() {
        if (this._loaded) return;
        this._loaded = !0;
        try { var t = localStorage.getItem("bili_cookies"); if (t) this._cookies = JSON.parse(t); } catch(e) {}
    },
    _save: function() { try { localStorage.setItem("bili_cookies", JSON.stringify(this._cookies)); } catch(e) {} },
    get: function(k) { this._load(); return this._cookies[k] || ""; },
    set: function(k, v) { this._load(); this._cookies[k] = v; this._save(); },
    setFromHeader: function(s) {
        if (!s) return; this._load();
        var parts = s.split(";");
        for (var i = 0; i < parts.length; i++) {
            var p = parts[i].trim();
            var eq = p.indexOf("=");
            if (eq > 0) {
                var k = p.substring(0, eq);
                var v = p.substring(eq + 1);
                if (!/^(path|domain|expires|secure|httponly|samesite|max-age|comment|discard)$/i.test(k))
                    this._cookies[k] = v;
            }
        }
        this._save();
    },
    getAll: function() { this._load(); var a = []; for (var k in this._cookies) a.push(k + "=" + this._cookies[k]); return a.join("; "); },
    clear: function() { this._cookies = {}; this._save(); }
};

var BILI_PROXY = "http://127.0.0.1:18081";

var BiliNet = {
    _xhr: function(method, path, callback, data) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, BILI_PROXY + path, true);
        if (data) xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 0) {
                    if (callback) callback(new Error("网络错误: 无法连接到代理 (status=0)"), null);
                    return;
                }
                var sc = xhr.getResponseHeader("Set-Cookie");
                if (sc) CookieStore.setFromHeader(sc);
                if (callback) callback(null, { status: xhr.status, text: xhr.responseText });
            }
        };
        xhr.onerror = function() { if (callback) callback(new Error("网络错误: XHR onerror"), null); };
        try { xhr.send(data || null); } catch(e) { if (callback) callback(new Error("网络错误: " + e.message), null); }
    },
    get: function(path, callback) { this._xhr("GET", path, callback); },
    getJson: function(path, callback) {
        this.get(path, function(err, resp) {
            if (err) { callback(err, null, 0); return; }
            var txt = resp.text;
            if (typeof txt !== "string") {
                callback(new Error("响应类型错误: " + typeof txt + " | status=" + resp.status), null, resp.status);
                return;
            }
            try { var j = JSON.parse(txt); callback(null, j, resp.status); }
            catch(e) {
                var eType = typeof e;
                var eStr = "";
                try { eStr = String(e); } catch(ex) { eStr = "无法转为字符串"; }
                var eMsg = "";
                try { eMsg = e.message; } catch(ex) { eMsg = "访问message异常"; }
                var preview = txt.length > 0 ? txt.substring(0, 300) : "(空字符串)";
                callback(new Error("JSON解析失败: type=" + eType + " | msg=" + eStr + " | e.message=" + eMsg + " | status=" + resp.status + " | text=" + preview), null, resp.status);
            }
        });
    },
    post: function(path, data, callback) { this._xhr("POST", path, callback, data); },
    postJson: function(path, data, callback) {
        this.post(path, data, function(err, resp) {
            if (err) { callback(err, null, 0); return; }
            try { var j = JSON.parse(resp.text); callback(null, j, resp.status); }
            catch(e) { callback(new Error("JSON: " + e.message), null, resp.status); }
        });
    },
    _rawXhr: function(method, url, callback, data) {
        var xhr = new XMLHttpRequest();
        xhr.open(method, url, true);
        if (data) xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                var sc = xhr.getResponseHeader("Set-Cookie");
                if (sc) CookieStore.setFromHeader(sc);
                if (callback) callback(null, { status: xhr.status, text: xhr.responseText });
            }
        };
        xhr.onerror = function() { if (callback) callback(new Error("XHR error"), null); };
        try { xhr.send(data || null); } catch(e) { if (callback) callback(new Error("XHR: " + e.message), null); }
    },
    postUrl: function(url, data, callback) { this._rawXhr("POST", url, callback, data); }
};

var WbiSign = {
    MIXIN_KEY_ENC_TAB: [46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52],
    _mixinKey: "", _lastDate: 0,

    _getDateCurr: function() { var d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); },

    _getFileFirstName: function(link) {
        var i = link.lastIndexOf("/");
        var f = link.substring(i + 1);
        var d = f.indexOf(".");
        return d >= 0 ? f.substring(0, d) : f;
    },

    _getMixinKey: function(raw) {
        var k = "";
        for (var i = 0; i < 32; i++) k += raw.charAt(this.MIXIN_KEY_ENC_TAB[i]);
        return k;
    },

    _sortParams: function(q) {
        if (!q) return "";
        var pairs = q.split("&"), map = {};
        for (var i = 0; i < pairs.length; i++) {
            var p = pairs[i];
            if (!p) continue;
            var eq = p.indexOf("=");
            if (eq >= 0) map[p.substring(0, eq)] = p.substring(eq + 1);
            else map[p] = "";
        }
        var keys = [], out = "";
        for (var k in map) keys.push(k);
        keys.sort();
        for (var j = 0; j < keys.length; j++) {
            if (j > 0) out += "&";
            out += keys[j] + "=" + map[keys[j]];
        }
        return out;
    },

    ensureKey: function(callback) {
        var self = this;
        BiliNet.getJson("/x/web-interface/nav", function(err, data) {
            if (!err && data && data.data && data.data.wbi_img) {
                var img = self._getFileFirstName(data.data.wbi_img.img_url);
                var sub = self._getFileFirstName(data.data.wbi_img.sub_url);
                self._mixinKey = self._getMixinKey(img + sub);
                self._lastDate = self._getDateCurr();
            } else {
                self._mixinKey = "604f662d63f4ee19c94bd8ac0de3f84d";
            }
            if (callback) callback(self._mixinKey);
        });
    },

    sign: function(urlQuery, callback) {
        var self = this;
        var curr = this._getDateCurr();

        function doSign(mk) {
            var wts = Math.floor(Date.now() / 1000) + "";
            var baseUrl = "", query = urlQuery;
            var qi = urlQuery.indexOf("?");
            if (qi >= 0) { baseUrl = urlQuery.substring(0, qi + 1); query = urlQuery.substring(qi + 1); }
            else { baseUrl = urlQuery + "?"; query = ""; }
            var paramStr = query.length > 0 ? query + "&wts=" + wts : "wts=" + wts;
            var sorted = self._sortParams(paramStr);
            var wrid = MD5(sorted + mk);
            if (callback) callback(baseUrl + sorted + "&w_rid=" + wrid);
        }

        if (this._lastDate < curr || !this._mixinKey) {
            this._lastDate = curr;
            this.ensureKey(function(mk) { doSign(mk); });
        } else {
            doSign(this._mixinKey);
        }
    }
};
