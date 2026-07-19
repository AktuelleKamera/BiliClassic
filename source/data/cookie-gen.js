var SHA256 = (function() {
    var K=[0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2];
    function ROTR(n,x){return (x>>>n)|(x<<(32-n))}
    function S0(x){return ROTR(2,x)^ROTR(13,x)^ROTR(22,x)}
    function S1(x){return ROTR(6,x)^ROTR(11,x)^ROTR(25,x)}
    function s0(x){return ROTR(7,x)^ROTR(18,x)^(x>>>3)}
    function s1(x){return ROTR(17,x)^ROTR(19,x)^(x>>>10)}
    function Ch(x,y,z){return (x&y)^(~x&z)}
    function Maj(x,y,z){return (x&y)^(x&z)^(y&z)}
    return function(msg) {
        var m=[],l=msg.length*8,H=[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19];
        for(var i=0;i<msg.length;i++)m[i>>2]=(m[i>>2]|0)+(msg.charCodeAt(i)<<(24-(i%4)*8));
        m[((l+64>>9)<<4)+15]=l;
        for(var i=0;i<m.length;i+=16){
            var W=[],a=H[0],b=H[1],c=H[2],d=H[3],e=H[4],f=H[5],g=H[6],h=H[7];
            for(var t=0;t<64;t++){W[t]=t<16?m[i+t|0]|0:((s1(W[t-2|0])+W[t-7|0]+s0(W[t-15|0])+W[t-16|0])|0);var T1=(h+S1(e)+Ch(e,f,g)+K[t]+W[t])|0,T2=(S0(a)+Maj(a,b,c))|0;h=g;g=f;f=e;e=(d+T1)|0;d=c;c=b;b=a;a=(T1+T2)|0;}
            H[0]=(H[0]+a)|0;H[1]=(H[1]+b)|0;H[2]=(H[2]+c)|0;H[3]=(H[3]+d)|0;H[4]=(H[4]+e)|0;H[5]=(H[5]+f)|0;H[6]=(H[6]+g)|0;H[7]=(H[7]+h)|0;
        }
        var hex="";
        for(var i=0;i<8;i++){var v=H[i];for(var j=28;j>=0;j-=4)hex+="0123456789abcdef".charAt((v>>j)&15);}
        return hex;
    };
})();

var HMAC_SHA256 = function(key, msg) {
    var blockSize = 64;
    if (key.length > blockSize) key = SHA256(key);
    while (key.length < blockSize) key += "\x00";
    var ipad = "", opad = "";
    for (var i = 0; i < blockSize; i++) {
        var kc = key.charCodeAt(i);
        ipad += String.fromCharCode(kc ^ 0x36);
        opad += String.fromCharCode(kc ^ 0x5c);
    }
    return SHA256(opad + SHA256(ipad + msg));
};

var CookieGen = {
    _ensuring: false,

    ensureCookies: function(callback) {
        if (this._ensuring) { if (callback) callback(); return; }
        this._ensuring = true;
        var self = this;

        function next() {
            if (CookieStore.get("buvid3")) { next2(); return; }
            BiliNet.getJson("/x/frontend/finger/spi", function(err, data) {
                if (!err && data && data.data) {
                    if (data.data.b_3) CookieStore.set("buvid3", data.data.b_3);
                    if (data.data.b_4) CookieStore.set("buvid4", data.data.b_4);
                }
                next2();
            });
        }

        function next2() {
            if (CookieStore.get("bili_ticket")) { next3(); return; }
            var ts = Math.floor(Date.now() / 1000);
            var hexsign = HMAC_SHA256("XgwSnGZ1p", "ts" + ts);
            BiliNet.getJson("/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket?key_id=ec02&hexsign=" + hexsign + "&context[ts]=" + ts, function(err, data) {
                if (!err && data && data.data && data.data.ticket) {
                    CookieStore.set("bili_ticket", data.data.ticket);
                    var expires = (data.data.created_at || ts) + 3 * 24 * 60 * 60;
                    CookieStore.set("bili_ticket_expires", String(expires));
                }
                next3();
            });
        }

        function next3() {
            if (!CookieStore.get("_uuid")) CookieStore.set("_uuid", self.genUuidInfoc());
            if (!CookieStore.get("b_lsid")) CookieStore.set("b_lsid", self.genBlsid());
            if (!CookieStore.get("buvid_fp")) CookieStore.set("buvid_fp", self.genBuvidFp());
            if (!CookieStore.get("b_nut")) CookieStore.set("b_nut", String(Math.floor(Date.now() / 1000)));
            self._ensuring = false;
            if (callback) callback();
        }

        next();
    },

    getCookieString: function(forVideoQuality) {
        var parts = [];
        function add(k, v) { if (v) parts.push(k + "=" + v); }
        var incognito = CookieStore.get("incognito_mode") === "true";
        if (!incognito || forVideoQuality) {
            var logged = CookieStore.get("cookies");
            if (logged) parts.push(logged);
        }
        add("buvid3", CookieStore.get("buvid3"));
        add("buvid4", CookieStore.get("buvid4"));
        add("bili_ticket", CookieStore.get("bili_ticket"));
        add("_uuid", CookieStore.get("_uuid"));
        add("b_lsid", CookieStore.get("b_lsid"));
        add("buvid_fp", CookieStore.get("buvid_fp"));
        add("b_nut", CookieStore.get("b_nut"));
        add("bili_ticket_expires", CookieStore.get("bili_ticket_expires"));
        return parts.join("; ");
    },

    genBlsid: function() {
        var chars = "0123456789ABCDEF", s = "";
        for (var i = 0; i < 8; i++) s += chars.charAt(Math.floor(Math.random() * chars.length));
        return s + "_" + (Date.now()).toString(16).toUpperCase();
    },

    genUuidInfoc: function() {
        var pck = [8, 4, 4, 4, 12], mp = ["1","2","3","4","5","6","7","8","9","A","B","C","D","E","F","10"];
        var sb = "";
        for (var n = 0; n < pck.length; n++) {
            for (var i = 0; i < pck[n]; i++) sb += mp[Math.floor(Math.random() * 16)];
            if (n < pck.length - 1) sb += "-";
        }
        var t = Date.now() % 100000;
        var pad = t < 10 ? "0000" : t < 100 ? "000" : t < 1000 ? "00" : t < 10000 ? "0" : "";
        return sb + pad + t + "infoc";
    },

    genBuvidFp: function() {
        function hex16(v) {
            var h = (v >>> 0).toString(16);
            while (h.length < 16) h = "0" + h;
            return h;
        }
        var now = Date.now();
        var nano = (typeof performance !== "undefined" && performance.now) ? Math.floor(performance.now() * 1000) : 0;
        var a = (now ^ 0x52DCE729) >>> 0;
        var b = (nano ^ 0x38495AB5) >>> 0;
        return hex16(a) + hex16(b);
    }
};
