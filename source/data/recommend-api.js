var RecommendApi = {
    _uniqId: Math.floor(1300000000000 + Math.random() * 200000000000),

    getRecommend: function(callback) {
        var path = "/x/web-interface/wbi/index/top/feed/rcmd?web_location=1430650&feed_version=V8&homepage_ver=1&uniq_id=" + this._uniqId + "&screen=1100-2056";
        var self = this;
        WbiSign.sign(path, function(signedPath) {
            BiliNet.getJson(signedPath, function(err, data) {
                if (err || !data || data.code !== 0) {
                    if (callback) callback(err || new Error("API error: " + (data ? data.code : "?")), null);
                    return;
                }
                var item = data.data && data.data.item ? data.data.item : [];
                var list = [];
                for (var i = 0; i < item.length; i++) {
                    var c = item[i];
                    if (!c.bvid) continue;
                    list.push({
                        aid: c.aid || 0,
                        bvid: c.bvid || "",
                        title: c.title || "",
                        cover: c.pic || "",
                        upName: c.owner ? c.owner.name : "",
                        view: self._toWan(c.stat ? c.stat.view : 0),
                        danmaku: c.stat ? c.stat.danmaku : 0
                    });
                }
                if (callback) callback(null, list);
            });
        });
    },

    getPopular: function(page, callback) {
        var path = "/x/web-interface/popular?pn=" + (page || 1) + "&ps=10";
        BiliNet.getJson(path, function(err, data) {
            if (err || !data || data.code !== 0) {
                if (callback) callback(err || new Error("API error"), null);
                return;
            }
            var list = data.data && data.data.list ? data.data.list : [];
            var result = [];
            for (var i = 0; i < list.length; i++) {
                var c = list[i];
                if (!c.aid) continue;
                result.push({
                    aid: c.aid || 0,
                    bvid: c.bvid || "",
                    title: c.title || "",
                    cover: c.pic || "",
                    upName: c.owner ? c.owner.name : "",
                    view: this._toWan(c.stat ? c.stat.view : 0),
                    danmaku: c.stat ? c.stat.danmaku : 0
                });
            }
            if (callback) callback(null, result);
        }.bind(this));
    },

    _toWan: function(n) {
        n = parseInt(n, 10) || 0;
        if (n >= 100000000) return (n / 100000000).toFixed(1) + "亿";
        if (n >= 10000) return (n / 10000).toFixed(1) + "万";
        return String(n);
    }
};
