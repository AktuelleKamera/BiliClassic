var HistoryApi = {
    getHistory: function(cursor, callback) {
        var viewAt = cursor && cursor.viewAt ? cursor.viewAt : 0;
        var business = cursor && cursor.business ? cursor.business : '';
        var offset = cursor && cursor.offset ? cursor.offset : 0;
        var path = "/x/web-interface/history/cursor?type=archive&view_at=" + viewAt + "&business=" + business + "&max=" + offset;
        BiliNet.getJson(path, function(err, data) {
            if (err || !data || data.code !== 0) {
                if (callback) callback(err || new Error("API error: " + (data ? data.code : "?")), null);
                return;
            }
            var list = data.data && data.data.list ? data.data.list : [];
            var items = [];
            for (var i = 0; i < list.length; i++) {
                var c = list[i];
                var history = c.history || {};
                var aid = history.oid || 0;
                var bvid = history.bvid || '';
                if (!aid && !bvid) continue;
                aid = parseInt(aid, 10);
                if (!aid || aid <= 0) continue;
                var progress = c.progress || 0;
                var viewStr;
                if (progress < 0) viewStr = '已看完';
                else if (progress === 0) viewStr = '还没看过';
                else viewStr = '看到 ' + HistoryApi._fmtTime(progress);
                items.push({
                    aid: aid,
                    bvid: bvid,
                    title: c.title || '',
                    cover: c.cover || '',
                    upName: c.author_name || '未知',
                    view: viewStr,
                    progress: progress
                });
            }
            var newCursor = { viewAt: 0, business: '', offset: 0 };
            if (data.data && data.data.cursor) {
                newCursor.viewAt = data.data.cursor.view_at || 0;
                newCursor.business = data.data.cursor.business || '';
                newCursor.offset = data.data.cursor.max || 0;
            }
            if (callback) callback(null, { items: items, cursor: newCursor, isBottom: !list || list.length === 0 });
        });
    },
    _fmtTime: function(s) {
        s = Math.floor(s || 0);
        var m = Math.floor(s / 60);
        var sec = s % 60;
        return m + ':' + (sec < 10 ? '0' : '') + sec;
    }
};
