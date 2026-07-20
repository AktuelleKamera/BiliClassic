var FavoriteApi = {
    _getCsrf: function() {
        var cookies = CookieStore.getAll();
        var m = cookies.match(/bili_jct=([a-f0-9]+)/);
        return m ? m[1] : "";
    },

    _getMid: function() {
        var cookies = CookieStore.getAll();
        var m = cookies.match(/DedeUserID=(\d+)/);
        return m ? parseInt(m[1], 10) : 0;
    },

    _getMidSuffix: function() {
        var strMid = String(this._getMid());
        if (strMid.length < 2) return "";
        return strMid.slice(-2);
    },

    getFavoriteFolders: function(callback) {
        var mid = this._getMid();
        if (!mid) {
            if (callback) callback(new Error("未登录"), null);
            return;
        }
        var path = "/x/v3/fav/folder/created/list-all?up_mid=" + mid + "&type=0";
        BiliNet.getJson(path, function(err, data) {
            if (err || !data || data.code !== 0) {
                if (callback) callback(err || new Error("获取收藏夹失败"), null);
                return;
            }
            var list = data.data && data.data.list ? data.data.list : [];
            var folders = [];
            for (var i = 0; i < list.length; i++) {
                var f = list[i];
                folders.push({
                    id: f.fid || f.id || 0,
                    name: f.title || "未命名收藏夹",
                    count: f.media_count || 0,
                    cover: f.cover || ""
                });
            }
            if (callback) callback(null, folders);
        });
    },

    getFolderVideos: function(fid, page, callback) {
        var mid = this._getMid();
        if (!mid) {
            if (callback) callback(new Error("未登录"), null);
            return;
        }
        var path = "/x/space/fav/arc?vmid=" + mid + "&ps=30&fid=" + fid + "&tid=0&keyword=&pn=" + page + "&order=fav_time";
        BiliNet.getJson(path, function(err, data) {
            if (err || !data || data.code !== 0) {
                if (callback) callback(err || new Error("获取收藏视频失败"), null);
                return;
            }
            if (!data.data || !data.data.archives) {
                if (callback) callback(null, []);
                return;
            }
            var list = data.data.archives;
            var items = [];
            for (var i = 0; i < list.length; i++) {
                var v = list[i];
                items.push({
                    aid: v.aid || 0,
                    bvid: v.bvid || "",
                    title: v.title || "",
                    cover: v.pic || "",
                    upName: (v.owner && v.owner.name) || "未知",
                    view: (v.stat && v.stat.view) || 0,
                    viewStr: VideoApi.toWan((v.stat && v.stat.view) || 0) + "播放"
                });
            }
            if (callback) callback(null, items);
        });
    },

    getFavoriteState: function(aid, callback) {
        var mid = this._getMid();
        if (!mid) {
            if (callback) callback(new Error("未登录"), null);
            return;
        }
        var path = "/x/v3/fav/folder/created/list-all?type=2&rid=" + aid + "&up_mid=" + mid;
        BiliNet.getJson(path, function(err, data) {
            if (err || !data || data.code !== 0) {
                if (callback) callback(err || new Error("获取收藏状态失败"), null);
                return;
            }
            var list = data.data && data.data.list ? data.data.list : [];
            var favState = { folders: [], favIds: [] };
            for (var i = 0; i < list.length; i++) {
                var f = list[i];
                favState.folders.push({
                    id: f.fid || 0,
                    name: f.title || "",
                    isFav: f.fav_state === 1
                });
                if (f.fav_state === 1) favState.favIds.push(f.fid || 0);
            }
            if (callback) callback(null, favState);
        });
    },

    addFavorite: function(aid, fid, callback) {
        var csrf = this._getCsrf();
        if (!csrf) {
            if (callback) callback(new Error("未登录或 csrf 无效"), null);
            return;
        }
        var midSuffix = this._getMidSuffix();
        if (!midSuffix) {
            if (callback) callback(new Error("mid 无效"), null);
            return;
        }
        var addFid = fid + midSuffix;
        var data = "rid=" + aid + "&type=2&add_media_ids=" + addFid + "&del_media_ids=&csrf=" + csrf;
        BiliNet.postJson("/x/v3/fav/resource/deal", data, function(err, result, status) {
            if (err) { if (callback) callback(err, null); return; }
            if (callback) callback(null, result && result.code === 0);
        });
    },

    deleteFavorite: function(aid, fid, callback) {
        var csrf = this._getCsrf();
        if (!csrf) {
            if (callback) callback(new Error("未登录或 csrf 无效"), null);
            return;
        }
        var midSuffix = this._getMidSuffix();
        if (!midSuffix) {
            if (callback) callback(new Error("mid 无效"), null);
            return;
        }
        var delFid = fid + midSuffix;
        var data = "resources=" + aid + ":2&media_id=" + delFid + "&csrf=" + csrf;
        BiliNet.postJson("/x/v3/fav/resource/batch-del", data, function(err, result, status) {
            if (err) { if (callback) callback(err, null); return; }
            if (callback) callback(null, result && result.code === 0);
        });
    }
};
