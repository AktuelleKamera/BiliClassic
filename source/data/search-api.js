var SearchApi = {
    _seid: "",
    _lastKeyword: "",

    search: function(keyword, page, callback) {
        if (this._lastKeyword !== keyword) {
            this._lastKeyword = keyword;
            this._seid = "";
        }
        var path = "/x/web-interface/search/type?page=" + page + "&pagesize=20&search_type=video&keyword=" + encodeURIComponent(keyword);
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, null); return; }
            if (!data || data.code !== 0 || !data.data) { if (callback) callback(new Error("搜索失败"), null); return; }
            var results = [];
            if (data.data.result) {
                for (var i = 0; i < data.data.result.length; i++) {
                    var card = data.data.result[i];
                    if (card.type !== "video") continue;
                    var title = (card.title || "").replace(/<em class="keyword">/g, "").replace(/<\/em>/g, "");
                    var cover = card.pic || "";
                    if (cover && cover.indexOf("http") !== 0) cover = "http:" + cover;
                    results.push({
                        title: title,
                        bvid: card.bvid || "",
                        aid: card.aid || 0,
                        cover: cover,
                        upName: card.author || "",
                        play: card.play || 0,
                        playStr: VideoApi.toWan(card.play || 0) + "播放"
                    });
                }
            }
            if (callback) callback(null, results);
        });
    },

    resolveInput: function(input) {
        var s = input.trim();
        if (/^av\d+$/i.test(s)) return { type: "av", id: parseInt(s.replace(/^av/i, ""), 10) };
        if (/^BV[a-zA-Z0-9]+$/i.test(s)) return { type: "bv", id: s.toUpperCase() };
        if (/^ep\d+$/i.test(s)) return { type: "ep", id: parseInt(s.replace(/^ep/i, ""), 10) };
        if (/^ss\d+$/i.test(s)) return { type: "ss", id: parseInt(s.replace(/^ss/i, ""), 10) };
        return { type: "search", keyword: s };
    }
};
