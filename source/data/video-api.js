var VideoApi = {
    toWan: function(num) {
        if (num >= 100000000) { var v = num / 100000000; return (v % 1 >= 0.1 ? Math.floor(v * 10) / 10 : Math.floor(v)) + "亿"; }
        if (num >= 10000) { var v = num / 10000; return (v % 1 >= 0.1 ? Math.floor(v * 10) / 10 : Math.floor(v)) + "万"; }
        return String(num);
    },

    getVideoInfo: function(bvid, callback) {
        var path = "/x/web-interface/view?bvid=" + bvid;
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, null); return; }
            if (!data || !data.data) { if (callback) callback(new Error("无数据"), null); return; }
            var vi = VideoApi.parseVideoInfo(data.data);
            if (callback) callback(null, vi);
        });
    },

    getVideoInfoByAid: function(aid, callback) {
        var path = "/x/web-interface/view?aid=" + aid;
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, null); return; }
            if (!data || !data.data) { if (callback) callback(new Error("无数据"), null); return; }
            var vi = VideoApi.parseVideoInfo(data.data);
            if (callback) callback(null, vi);
        });
    },

    getTags: function(bvid, callback) {
        var path = "/x/tag/archive/tags?bvid=" + bvid;
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, ""); return; }
            if (!data || !data.data) { if (callback) callback(null, ""); return; }
            var tags = [];
            for (var i = 0; i < data.data.length; i++) tags.push(data.data[i].tag_name || "");
            if (callback) callback(null, tags.join("/"));
        });
    },

    getTagsByAid: function(aid, callback) {
        var path = "/x/tag/archive/tags?aid=" + aid;
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, ""); return; }
            if (!data || !data.data) { if (callback) callback(null, ""); return; }
            var tags = [];
            for (var i = 0; i < data.data.length; i++) tags.push(data.data[i].tag_name || "");
            if (callback) callback(null, tags.join("/"));
        });
    },

    parseVideoInfo: function(data) {
        var vi = {};
        vi.title = data.title || "";
        vi.cover = data.pic || "";
        vi.bvid = data.bvid || "";
        vi.aid = data.aid || 0;
        vi.pubdate = data.pubdate || 0;
        vi.copyright = data.copyright || 0;
        vi.duration = data.duration || 0;
        vi.timeDesc = "";
        if (vi.pubdate) {
            var d = new Date(vi.pubdate * 1000);
            vi.timeDesc = d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate()) + " " + pad2(d.getHours()) + ":" + pad2(d.getMinutes()) + ":" + pad2(d.getSeconds());
        }

        if (data.desc_v2 && data.desc_v2.length > 0) {
            vi.description = "";
            vi.descAts = [];
            for (var i = 0; i < data.desc_v2.length; i++) {
                var item = data.desc_v2[i];
                if (item.type === 2) {
                    var start = vi.description.length;
                    vi.description += "@" + (item.raw_text || "");
                    vi.descAts.push({ biz_id: item.biz_id, start: start, end: vi.description.length });
                } else {
                    vi.description += (item.raw_text || "");
                }
            }
        } else {
            vi.description = data.desc || "";
        }

        vi.stats = data.stat ? {
            view: data.stat.view || 0,
            like: data.stat.like || 0,
            coin: data.stat.coin || 0,
            reply: data.stat.reply || 0,
            danmaku: data.stat.danmaku || 0,
            favorite: data.stat.favorite || -1,
            coin_limit: vi.copyright === 2 ? 1 : 2
        } : {};

        vi.pagenames = [];
        vi.cids = [];
        if (data.pages) {
            for (var i = 0; i < data.pages.length; i++) {
                vi.pagenames.push(data.pages[i].part || "");
                vi.cids.push(data.pages[i].cid || 0);
            }
        }

        vi.upowerExclusive = !!data.is_upower_exclusive;
        vi.argueMsg = (data.argue_info && data.argue_info.argue_msg) || "";
        vi.isCooperation = data.rights ? data.rights.is_cooperation === 1 : false;
        vi.isSteinGate = data.rights ? data.rights.is_stein_gate === 1 : false;
        vi.is360 = data.rights ? data.rights.is_360 === 1 : false;

        vi.qualities = [];
        if (data.accept_quality) {
            for (var i = 0; i < data.accept_quality.length; i++) {
                var q = data.accept_quality[i];
                if (vi.qualities.indexOf(q) < 0) vi.qualities.push(q);
            }
        }
        if (data.dash && data.dash.video) {
            for (var i = 0; i < data.dash.video.length; i++) {
                var id = data.dash.video[i].id || 0;
                if (id > 0 && vi.qualities.indexOf(id) < 0) vi.qualities.push(id);
            }
        }
        if (vi.qualities.length === 0) vi.qualities = [16, 32, 64];
        vi.qualities.sort(function(a, b) { return a - b; });

        vi.staff = [];
        if (data.staff) {
            for (var i = 0; i < data.staff.length; i++) {
                var s = data.staff[i];
                vi.staff.push({
                    mid: s.mid || 0, name: s.name || "", avatar: s.face || "",
                    sign: s.title || "", fans: s.follower || 0, level: 6,
                    followed: false, notice: "",
                    official: s.official ? s.official.role || 0 : 0,
                    officialDesc: s.official ? s.official.title || "" : ""
                });
            }
        } else if (data.owner) {
            vi.staff.push({
                mid: data.owner.mid || 0, name: data.owner.name || "",
                avatar: data.owner.face || "", sign: "UP主"
            });
        }

        vi.epid = -1;
        if (data.redirect_url && data.redirect_url.indexOf("bangumi") >= 0) {
            var m = data.redirect_url.match(/ep(\d+)/);
            if (m) vi.epid = parseInt(m[1], 10);
        }

        vi.collection = null;
        if (data.ugc_season) vi.collection = VideoApi.parseUgcSeason(data.ugc_season);

        return vi;
    },

    parseUgcSeason: function(json) {
        var c = { id: json.id || -1, title: json.title || "", intro: json.intro || "", cover: json.cover || "", mid: json.mid || 0, view: "", sections: [] };
        if (json.stat) c.view = VideoApi.toWan(json.stat.view || 0);
        if (json.sections) {
            for (var i = 0; i < json.sections.length; i++) {
                var sj = json.sections[i], sec = { season_id: sj.season_id || -1, id: sj.id || -1, title: sj.title || "", episodes: [] };
                if (sj.episodes) {
                    for (var j = 0; j < sj.episodes.length; j++) {
                        var ej = sj.episodes[j], ep = {
                            season_id: ej.season_id || -1, section_id: ej.section_id || -1,
                            id: ej.id || -1, aid: ej.aid || -1, cid: ej.cid || -1,
                            title: ej.title || "", bvid: ej.bvid || "", arc: null
                        };
                        if (ej.arc) ep.arc = VideoApi.parseVideoInfo(ej.arc);
                        sec.episodes.push(ep);
                    }
                }
                c.sections.push(sec);
            }
        }
        return c;
    },

    getWatching: function(aid, cid, callback) {
        var path = "/x/player/online/total?aid=" + aid + "&cid=" + cid;
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(null, ""); return; }
            if (!data || !data.data || data.data.total === undefined || data.data.total === null) { if (callback) callback(null, ""); return; }
            var total = data.data.total;
            if (typeof total === "string") { if (callback) callback(null, total); return; }
            if (typeof total === "number") { if (callback) callback(null, VideoApi.toWan(total)); return; }
            if (callback) callback(null, "");
        });
    },

    getPlayUrl: function(aid, cid, qn, callback) {
        var path = "/x/player/wbi/playurl?avid=" + aid + "&cid=" + cid + "&qn=" + (qn || 16) + "&fnval=0&fnver=0&platform=html5&gaia_source=pre-load&isGaiaAvoided=true";
        var self = this;
        WbiSign.sign(path, function(signedPath) {
            BiliNet.getJson(signedPath, function(err, data) {
                if (err) { if (callback) callback(err, null); return; }
                if (!data || data.code !== 0) { if (callback) callback(new Error("API错误: " + (data ? data.message : "未知")), null); return; }
                var result = {};
                if (data.data.durl && data.data.durl.length > 0) {
                    result.videoUrl = data.data.durl[0].url;
                } else if (data.data.dash && data.data.dash.video && data.data.dash.video.length > 0) {
                    var first = data.data.dash.video[0];
                    result.videoUrl = (first.backupUrl && first.backupUrl.length > 0) ? first.backupUrl[0] : (first.baseUrl || "");
                }
                result.qnStrList = data.data.accept_description || [];
                result.qnValueList = data.data.accept_quality || [];
                result.lastPlayCid = data.data.last_play_cid || 0;
                result.progress = data.data.last_play_time || 0;
                result.danmakuUrl = "https://comment.bilibili.com/" + cid + ".xml";
                if (callback) callback(null, result);
            });
        });
    },

    getBangumiUrl: function(aid, cid, qn, callback) {
        var path = "/pgc/player/web/playurl?aid=" + aid + "&cid=" + cid + "&fnval=4048&fnvar=0&qn=" + (qn || 16) + "&season_type=1&platform=pc";
        BiliNet.getJson(path, function(err, data) {
            if (err) { if (callback) callback(err, null); return; }
            if (!data || data.code !== 0 || !data.result) { if (callback) callback(new Error("API错误"), null); return; }
            var result = {};
            if (data.result.durl && data.result.durl.length > 0) {
                result.videoUrl = data.result.durl[0].url;
            }
            result.qnStrList = data.result.accept_description || [];
            result.qnValueList = data.result.accept_quality || [];
            result.danmakuUrl = "https://comment.bilibili.com/" + cid + ".xml";
            if (callback) callback(null, result);
        });
    }
};

function pad2(n) { return n < 10 ? "0" + n : "" + n; }
