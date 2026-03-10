const DAY_NAMES = ['Pondělí', 'Úterý', 'Středa', 'Čtvrtek', 'Pátek', 'Sobota', 'Neděle'];

const DISCUSSION_NAMES = {
    '11354': 'Poznej PC hru',
    '7045': 'Zábavný kvíz',
};

const SLUG_TO_ID = {
    '/poznej-pc-hru': '11354',
    '/zabavny-kviz': '7045',
};

function getDiscussionId() {
    return SLUG_TO_ID[window.location.pathname]
        || new URLSearchParams(window.location.search).get('discussion')
        || '11354';
}

function topN(counts, n) {
    return Object.entries(counts)
        .filter(([k]) => k && k !== 'null')
        .sort((a, b) => b[1] - a[1])
        .slice(0, n);
}

let activeDowFilter = new Set();
let activeHourFilter = new Set();
let activeReceiverFilter = new Set();
let activeGiverFilter = new Set();

// Each filter function only applies its own filter
// Charts receive points filtered by all filters EXCEPT their own,
// so they always show their full distribution with the active selection highlighted

function applyFilters(points, { excludeDow = false, excludeHour = false, excludeReceiver = false, excludeGiver = false } = {}) {
    let filtered = points;
    if (!excludeDow && activeDowFilter.size > 0)
        filtered = filtered.filter(p => activeDowFilter.has((new Date(p.givenDateTime.substring(0, 10)).getDay() + 6) % 7));
    if (!excludeHour && activeHourFilter.size > 0)
        filtered = filtered.filter(p => activeHourFilter.has(parseInt(p.givenDateTime.substring(11, 13))));
    if (!excludeReceiver && activeReceiverFilter.size > 0)
        filtered = filtered.filter(p => activeReceiverFilter.has(p.givenTo));
    if (!excludeGiver && activeGiverFilter.size > 0)
        filtered = filtered.filter(p => activeGiverFilter.has(p.givenBy));
    return filtered;
}

function buildTimeseriesData(points, allPoints) {
    const byDay = {};
    points.forEach(p => {
        const day = p.givenDateTime.substring(0, 10);
        byDay[day] = (byDay[day] || 0) + 1;
    });
    const sortedAll = allPoints.map(p => p.givenDateTime.substring(0, 10)).sort();
    if (!sortedAll.length) return [];
    const firstDay = new Date(sortedAll[0]);
    const lastDay = new Date(sortedAll[sortedAll.length - 1]);
    const data = [];
    for (let d = new Date(firstDay); d <= lastDay; d.setDate(d.getDate() + 1)) {
        const key = d.toISOString().substring(0, 10);
        data.push([d.getTime(), byDay[key] || 0]);
    }
    return data;
}

function updateDowChart(dowChart, points) {
    const byDow = [0, 0, 0, 0, 0, 0, 0];
    points.forEach(p => {
        const d = new Date(p.givenDateTime.substring(0, 10));
        byDow[(d.getDay() + 6) % 7]++;
    });
    dowChart.series[0].setData(byDow.map((v, i) => ({
        y: v,
        color: activeDowFilter.size === 0 || activeDowFilter.has(i) ? '#90ed7d' : 'rgba(144,237,125,0.3)',
    })), true);
}

function updateHourlyChart(hourlyChart, points) {
    const byHour = Array(24).fill(0);
    points.forEach(p => {
        byHour[parseInt(p.givenDateTime.substring(11, 13))]++;
    });
    hourlyChart.yAxis[0].setExtremes(0, Math.max(...byHour), true, false, { endOnTick: false });
    hourlyChart.series[0].setData(byHour.map((v, i) => ({
        y: v,
        color: activeHourFilter.size === 0 || activeHourFilter.has(i) ? '#ffd700' : 'rgba(255,215,0,0.3)',
    })), true);
}

function updateReceiversChart(receiversChart, points) {
    const counts = {};
    points.forEach(p => {
        if (p.givenTo) counts[p.givenTo] = (counts[p.givenTo] || 0) + 1;
    });
    const top = topN(counts, 20);
    receiversChart.xAxis[0].setCategories(top.map(([k]) => k));
    receiversChart.series[0].setData(top.map(([k, v]) => ({
        y: v,
        color: activeReceiverFilter.size === 0 || activeReceiverFilter.has(k) ? '#f7a35c' : 'rgba(247,163,92,0.3)',
    })), true);
    receiversChart.setSize(null, top.length * 20 + 100);
}

function updateGiversChart(giversChart, points) {
    const counts = {};
    points.forEach(p => {
        if (p.givenBy) counts[p.givenBy] = (counts[p.givenBy] || 0) + 1;
    });
    const top = topN(counts, 20);
    giversChart.xAxis[0].setCategories(top.map(([k]) => k));
    giversChart.series[0].setData(top.map(([k, v]) => ({
        y: v,
        color: activeGiverFilter.size === 0 || activeGiverFilter.has(k) ? '#8085e9' : 'rgba(128,133,233,0.3)',
    })), true);
    giversChart.setSize(null, top.length * 20 + 100);
}

function refreshSecondaryCharts(timeFiltered, dowChart, hourlyChart, receiversChart, giversChart) {
    updateDowChart(dowChart, applyFilters(timeFiltered, { excludeDow: true }));
    updateHourlyChart(hourlyChart, applyFilters(timeFiltered, { excludeHour: true }));
    updateReceiversChart(receiversChart, applyFilters(timeFiltered, { excludeReceiver: true }));
    updateGiversChart(giversChart, applyFilters(timeFiltered, { excludeGiver: true }));
}

function computeStats(allPoints) {
    // Build byDay map (YYYY-MM-DD → count)
    const byDay = {};
    allPoints.forEach(p => {
        const d = p.givenDateTime.substring(0, 10);
        byDay[d] = (byDay[d] || 0) + 1;
    });

    // Sorted array of all days (first → last, filling gaps)
    const sorted = Object.keys(byDay).sort();
    if (!sorted.length) return [];
    const first = new Date(sorted[0]);
    const last  = new Date(sorted[sorted.length - 1]);
    const days = [];  // [{date: 'YYYY-MM-DD', count: n}, ...]
    for (let d = new Date(first); d <= last; d.setDate(d.getDate() + 1)) {
        const key = d.toISOString().substring(0, 10);
        days.push({ date: key, count: byDay[key] || 0 });
    }

    // Helper: Czech date format
    function fmt(dateStr) {
        const [y, m, d] = dateStr.split('-');
        return `${+d}.${+m}.${y}`;
    }
    function fmtRange(a, b) {
        const [ay, am, ad] = a.split('-');
        const [by, bm, bd] = b.split('-');
        const left = ay === by ? `${+ad}.${+am}.` : fmt(a);
        return `${left} – ${fmt(b)}`;
    }

    // 1. Longest active streak (consecutive days with count > 0)
    let bestActiveLen = 0, bestActiveStart = '', bestActiveEnd = '';
    let curLen = 0, curStart = '';
    days.forEach(({ date, count }) => {
        if (count > 0) {
            if (curLen === 0) curStart = date;
            curLen++;
            if (curLen > bestActiveLen) {
                bestActiveLen = curLen;
                bestActiveStart = curStart;
                bestActiveEnd = date;
            }
        } else { curLen = 0; }
    });

    // 2. Longest quiet streak (consecutive days with count === 0)
    let bestQuietLen = 0, bestQuietStart = '', bestQuietEnd = '';
    curLen = 0; curStart = '';
    days.forEach(({ date, count }) => {
        if (count === 0) {
            if (curLen === 0) curStart = date;
            curLen++;
            if (curLen > bestQuietLen) {
                bestQuietLen = curLen;
                bestQuietStart = curStart;
                bestQuietEnd = date;
            }
        } else { curLen = 0; }
    });

    // 3. Busiest single day
    const busiestDay = days.reduce((a, b) => b.count > a.count ? b : a);

    // 4. Best 7-day sliding window
    let best7 = 0, best7Start = '', best7End = '';
    let win = 0;
    for (let i = 0; i < days.length; i++) {
        win += days[i].count;
        if (i >= 7) win -= days[i - 7].count;
        const windowSize = Math.min(i + 1, 7);
        if (windowSize === 7 && win > best7) {
            best7 = win;
            best7Start = days[i - 6].date;
            best7End   = days[i].date;
        }
    }

    // 5. Best calendar month
    const byMonth = {};
    days.forEach(({ date, count }) => {
        const m = date.substring(0, 7); // YYYY-MM
        byMonth[m] = (byMonth[m] || 0) + count;
    });
    const MONTH_NAMES = ['', 'Leden','Únor','Březen','Duben','Květen','Červen',
                         'Červenec','Srpen','Září','Říjen','Listopad','Prosinec'];
    const bestMonthKey = Object.keys(byMonth).reduce((a, b) => byMonth[b] > byMonth[a] ? b : a);
    const [bmy, bmm] = bestMonthKey.split('-');
    const bestMonthLabel = `${MONTH_NAMES[+bmm]} ${bmy}`;

    return [
        { metric: 'Nejdelší aktivní série',       value: `${bestActiveLen} dní`,  date: fmtRange(bestActiveStart, bestActiveEnd) },
        { metric: 'Nejdelší pauza bez bodu',       value: `${bestQuietLen} dní`,   date: fmtRange(bestQuietStart, bestQuietEnd) },
        { metric: 'Nejvíce bodů za den',           value: `${busiestDay.count} bodů`, date: fmt(busiestDay.date) },
        { metric: 'Nejvíce bodů za týden',         value: `${best7} bodů`,         date: fmtRange(best7Start, best7End) },
        { metric: 'Nejaktivnější měsíc',           value: `${byMonth[bestMonthKey]} bodů`, date: bestMonthLabel },
    ];
}

function renderCharts(data) {
    const allPoints = data.points.filter(p => p.givenDateTime);

    const stats = computeStats(allPoints);
    const tbody = document.querySelector('#stats-table tbody');
    stats.forEach(({ metric, value, date }) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `<td>${metric}</td><td>${value}</td><td>${date}</td>`;
        tbody.appendChild(tr);
    });

    const discussionName = DISCUSSION_NAMES[String(data.discussionId)] || `Diskuze ${data.discussionId}`;
    document.title = `Statistiky bodů — ${discussionName}`;
    document.querySelector('h1').textContent = `Statistiky bodů — ${discussionName}`;
    document.getElementById('status').textContent =
        `${allPoints.length} bodů — vygenerováno ${data.generatedAt}`;

    function getTimeFiltered() {
        const { min, max } = stockChart.xAxis[0].getExtremes();
        return allPoints.filter(p => {
            const t = new Date(p.givenDateTime.substring(0, 10)).getTime();
            return t >= min && t <= max;
        });
    }

    function updateFilterBar() {
        const bar = document.getElementById('filter-bar');
        bar.innerHTML = '';
        const active = [
            activeDowFilter.size > 0 && {
                label: 'Den',
                value: [...activeDowFilter].sort().map(i => DAY_NAMES[i]).join(', '),
                clear: () => activeDowFilter.clear(),
            },
            activeHourFilter.size > 0 && {
                label: 'Hodina',
                value: [...activeHourFilter].sort((a, b) => a - b).map(h => String(h).padStart(2, '0') + ':00').join(', '),
                clear: () => activeHourFilter.clear(),
            },
            activeReceiverFilter.size > 0 && {
                label: 'Obdržel',
                value: [...activeReceiverFilter].join(', '),
                clear: () => activeReceiverFilter.clear(),
            },
            activeGiverFilter.size > 0 && {
                label: 'Udělil',
                value: [...activeGiverFilter].join(', '),
                clear: () => activeGiverFilter.clear(),
            },
        ].filter(Boolean);
        active.forEach(f => {
            const tag = document.createElement('span');
            tag.className = 'filter-tag';
            tag.innerHTML = `<span class="label">${f.label}:</span> ${f.value} <button title="Zrušit filtr">×</button>`;
            tag.querySelector('button').addEventListener('click', () => { f.clear(); onFilterChange(); });
            bar.appendChild(tag);
        });
        if (active.length > 1) {
            const btn = document.createElement('button');
            btn.id = 'btn-reset-all';
            btn.textContent = 'Zrušit vše';
            btn.addEventListener('click', () => {
                activeDowFilter.clear(); activeHourFilter.clear();
                activeReceiverFilter.clear(); activeGiverFilter.clear();
                onFilterChange();
            });
            bar.appendChild(btn);
        }
    }

    function onFilterChange() {
        updateFilterBar();
        const timeFiltered = getTimeFiltered();
        refreshSecondaryCharts(timeFiltered, dowChart, hourlyChart, receiversChart, giversChart);
        stockChart.series[0].setData(buildTimeseriesData(applyFilters(allPoints), allPoints), true);
    }

    const dowChart = Highcharts.chart('chart-dayofweek', {
        title: { text: 'Body podle dne v týdnu' },
        xAxis: { categories: DAY_NAMES },
        yAxis: { title: { text: 'Celkem bodů' }, min: 0 },
        plotOptions: {
            series: {
                cursor: 'pointer',
                point: {
                    events: {
                        click: function () {
                            if (activeDowFilter.has(this.index)) activeDowFilter.delete(this.index);
                            else activeDowFilter.add(this.index);
                            onFilterChange();
                        },
                    },
                },
            },
        },
        series: [{ name: 'Body', data: [0, 0, 0, 0, 0, 0, 0], type: 'column', color: '#90ed7d' }],
        legend: { enabled: false },
        credits: { enabled: false },
    });

    const hourlyChart = Highcharts.chart('chart-hourly', {
        chart: { polar: true },
        title: { text: 'Body podle hodiny' },
        xAxis: {
            categories: ['00','01','02','03','04','05','06','07','08','09','10','11',
                         '12','13','14','15','16','17','18','19','20','21','22','23'],
            tickmarkPlacement: 'on',
        },
        yAxis: { min: 0, max: 1, endOnTick: false, maxPadding: 0, gridLineInterpolation: 'polygon', title: { text: '' }, labels: { enabled: false } },
        plotOptions: {
            series: {
                cursor: 'pointer',
                point: {
                    events: {
                        click: function () {
                            if (activeHourFilter.has(this.index)) activeHourFilter.delete(this.index);
                            else activeHourFilter.add(this.index);
                            onFilterChange();
                        },
                    },
                },
            },
        },
        series: [{
            name: 'Body',
            data: Array(24).fill(0),
            type: 'column',
            color: '#ffd700',
            pointPadding: 0,
            groupPadding: 0,
        }],
        legend: { enabled: false },
        credits: { enabled: false },
    });

    const receiversChart = Highcharts.chart('chart-top-receivers', {
        title: { text: 'Obdržené body' },
        xAxis: { categories: [], labels: { rotation: 0 } },
        yAxis: { title: { text: 'Přijatých bodů' }, min: 0 },
        plotOptions: {
            series: {
                cursor: 'pointer',
                point: {
                    events: {
                        click: function () {
                            const name = this.series.xAxis.categories[this.index];
                            if (activeReceiverFilter.has(name)) activeReceiverFilter.delete(name);
                            else activeReceiverFilter.add(name);
                            onFilterChange();
                        },
                    },
                },
            },
        },
        series: [{ name: 'Přijato', data: [], type: 'bar', color: '#f7a35c' }],
        legend: { enabled: false },
        credits: { enabled: false },
    });

    const giversChart = Highcharts.chart('chart-top-givers', {
        title: { text: 'Udělené body' },
        xAxis: { categories: [], labels: { rotation: 0 } },
        yAxis: { title: { text: 'Udělených bodů' }, min: 0 },
        plotOptions: {
            series: {
                cursor: 'pointer',
                point: {
                    events: {
                        click: function () {
                            const name = this.series.xAxis.categories[this.index];
                            if (activeGiverFilter.has(name)) activeGiverFilter.delete(name);
                            else activeGiverFilter.add(name);
                            onFilterChange();
                        },
                    },
                },
            },
        },
        series: [{ name: 'Uděleno', data: [], type: 'bar', color: '#8085e9' }],
        legend: { enabled: false },
        credits: { enabled: false },
    });

    refreshSecondaryCharts(allPoints, dowChart, hourlyChart, receiversChart, giversChart);

    const autoDataGrouping = {
        enabled: true, forced: false, approximation: 'sum', groupPixelWidth: 10,
        units: [['day', [1, 2, 3, 5]], ['week', [1, 2]], ['month', [1, 2, 3]]]
    };

    function applyAggregation(type) {
        const units = {
            auto: null,
            '1d': ['day',   [1]],
            '3d': ['day',   [3]],
            '1w': ['week',  [1]],
            '2w': ['week',  [2]],
            '1m': ['month', [1]]
        };
        const unit = units[type];
        stockChart.series[0].update({
            dataGrouping: unit
                ? { enabled: true, forced: true, approximation: 'sum', units: [unit] }
                : autoDataGrouping
        }, true);
    }

    const stockChart = Highcharts.stockChart('chart-timeseries', {
        chart: { zoomType: 'x' },
        title: { text: 'Body v čase' },
        rangeSelector: {
            buttons: [
                { type: 'month', count: 3, text: '3M' },
                { type: 'month', count: 6, text: '6M' },
                { type: 'year', count: 1, text: '1R' },
                { type: 'year', count: 2, text: '2R' },
                { type: 'all', text: 'Vše' },
            ],
            selected: 4,
        },
        xAxis: {
            type: 'datetime',
            events: {
                afterSetExtremes: function (e) {
                    const timeFiltered = allPoints.filter(p => {
                        const t = new Date(p.givenDateTime.substring(0, 10)).getTime();
                        return t >= e.min && t <= e.max;
                    });
                    refreshSecondaryCharts(timeFiltered, dowChart, hourlyChart, receiversChart, giversChart);
                },
            },
        },
        yAxis: { title: { text: 'Bodů za den' }, min: 0 },
        tooltip: { xDateFormat: '%Y-%m-%d', valueSuffix: ' b' },
        series: [{
            name: 'Body',
            data: buildTimeseriesData(allPoints, allPoints),
            type: 'line',
            color: '#7cb5ec',
            dataGrouping: autoDataGrouping
        }],
        legend: { enabled: false },
        credits: { enabled: false },
    });

    const autoBtn = document.querySelector('#aggregation-bar .agg-btn[data-agg="auto"]');
    const unitLabel = { day: 'D', week: 'T', month: 'M' };
    function updateAutoLabel() {
        if (!autoBtn.classList.contains('active')) {
            autoBtn.textContent = 'Auto';
            return;
        }
        const dg = stockChart.series[0].currentDataGrouping;
        const label = dg ? `${dg.count}${unitLabel[dg.unitName] || dg.unitName}` : '1D';
        autoBtn.textContent = `Auto (${label})`;
    }
    Highcharts.addEvent(stockChart, 'render', updateAutoLabel);
    updateAutoLabel();

    document.querySelectorAll('#aggregation-bar .agg-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('#aggregation-bar .agg-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            applyAggregation(btn.dataset.agg);
        });
    });
}

const discussionId = getDiscussionId();
fetch(`/data/${discussionId}.json`)
    .then(r => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
    })
    .then(renderCharts)
    .catch(err => {
        document.getElementById('status').textContent = `Chyba při načítání dat: ${err.message}`;
        console.error(err);
    });
