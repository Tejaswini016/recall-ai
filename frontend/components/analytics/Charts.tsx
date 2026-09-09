"use client";

import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ActivityPoint, MasteryPoint, TopicPerformance } from "@/types";
import { formatShortDate } from "@/lib/format";

const SERIES = "var(--chart-1)";
const CRITICAL = "var(--chart-critical)";
const GRID = "var(--border)";
const INK = "var(--muted)";

const axisProps = { tick: { fill: INK, fontSize: 11 }, axisLine: false, tickLine: false } as const;
const tooltipStyle = {
  contentStyle: {
    background: "var(--card)",
    border: "1px solid var(--border)",
    borderRadius: 8,
    fontSize: 12,
    color: "var(--foreground)",
  },
  labelStyle: { color: "var(--muted)" },
  cursor: { stroke: GRID },
} as const;

function tickEvery(length: number): number {
  return Math.max(0, Math.ceil(length / 7) - 1);
}

/** Reviews per day. */
export function ActivityChart({ data, height = 260 }: { data: ActivityPoint[]; height?: number }) {
  if (data.every((d) => d.reviews === 0)) {
    return <ChartEmpty height={height} label="No reviews in this period yet." />;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="date" tickFormatter={formatShortDate} interval={tickEvery(data.length)} {...axisProps} />
        <YAxis allowDecimals={false} {...axisProps} />
        <Tooltip
          {...tooltipStyle}
          labelFormatter={(label) => formatShortDate(String(label))}
          formatter={(value, name) => [value, name === "reviews" ? "Reviews" : "Successful"]}
        />
        <Area type="monotone" dataKey="reviews" stroke={SERIES} strokeWidth={2} fill={SERIES} fillOpacity={0.15} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/** Share of successful reviews per day; days without reviews are gaps. */
export function RetentionChart({ data, height = 260 }: { data: ActivityPoint[]; height?: number }) {
  if (data.every((d) => d.retentionPercent === null)) {
    return <ChartEmpty height={height} label="Retention appears once you have reviewed cards." />;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="date" tickFormatter={formatShortDate} interval={tickEvery(data.length)} {...axisProps} />
        <YAxis domain={[0, 100]} tickFormatter={(v) => `${v}%`} {...axisProps} />
        <Tooltip
          {...tooltipStyle}
          labelFormatter={(label) => formatShortDate(String(label))}
          formatter={(value) => [`${value}%`, "Retention"]}
        />
        <Line
          type="monotone"
          dataKey="retentionPercent"
          stroke={SERIES}
          strokeWidth={2}
          dot={{ r: 3, fill: SERIES, strokeWidth: 0 }}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Cumulative mastered cards. */
export function MasteryChart({ data, height = 260 }: { data: MasteryPoint[]; height?: number }) {
  if (data.length === 0 || data[data.length - 1].masteredCards === 0) {
    return <ChartEmpty height={height} label="Cards count as mastered once their interval reaches 21 days." />;
  }
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="date" tickFormatter={formatShortDate} interval={tickEvery(data.length)} {...axisProps} />
        <YAxis allowDecimals={false} {...axisProps} />
        <Tooltip
          {...tooltipStyle}
          labelFormatter={(label) => formatShortDate(String(label))}
          formatter={(value) => [value, "Mastered cards"]}
        />
        <Area type="stepAfter" dataKey="masteredCards" stroke={SERIES} strokeWidth={2} fill={SERIES} fillOpacity={0.15} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/** Recent average quality per topic; weak topics are drawn in the reserved status colour. */
export function TopicChart({ data, height = 280 }: { data: TopicPerformance[]; height?: number }) {
  if (data.length === 0) {
    return <ChartEmpty height={height} label="Topics appear after you review cards that have a topic." />;
  }
  const rows = data.slice(0, 12);
  return (
    <ResponsiveContainer width="100%" height={Math.max(height, rows.length * 28)}>
      <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" domain={[0, 5]} ticks={[0, 1, 2, 3, 4, 5]} {...axisProps} />
        <YAxis type="category" dataKey="topic" width={120} {...axisProps} />
        <Tooltip
          {...tooltipStyle}
          cursor={{ fill: "var(--border)", opacity: 0.4 }}
          formatter={(value, _name, item) => {
            const topic = item.payload as TopicPerformance;
            return [`${Number(value).toFixed(2)} / 5 (${topic.reviews} reviews)`, "Recent average"];
          }}
        />
        <Bar dataKey="recentAverageQuality" radius={[0, 4, 4, 0]} maxBarSize={18}>
          {rows.map((row) => (
            <Cell key={row.topic} fill={row.weak ? CRITICAL : SERIES} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function ChartEmpty({ height, label }: { height: number; label: string }) {
  return (
    <div className="flex items-center justify-center text-center text-sm text-muted" style={{ height }}>
      {label}
    </div>
  );
}
