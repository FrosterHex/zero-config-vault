"use client";

import React, { useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import "./landing.css";
import { useVault } from "@/context/VaultContext";
import { LoginDialog } from "@/components/auth/LoginDialog";
import { PlanetCanvas } from "@/components/landing/PlanetCanvas";
import { ArrowIcon, FeatureIcon } from "@/components/landing/Icons";
import { useLenis, usePrefersReducedMotion, useReveal } from "@/components/landing/useLandingMotion";
import { BUILT_ON, FEATURES, FOOTER_COLUMNS, NAV_LINKS, toBars, toEnvRows } from "@/components/landing/landing-content";

const rd = (ms: number) => ({ "--rd": `${ms}ms` }) as React.CSSProperties;

function Brand() {
  return (
    <span className="brand">
      <span className="brand-mark" aria-hidden="true">▲</span>
      <span>Zero-Config Vault</span>
    </span>
  );
}

export default function LandingPage() {
  const router = useRouter();
  const { stats, role } = useVault();
  const reducedMotion = usePrefersReducedMotion();
  const rootRef = useRef<HTMLDivElement>(null);
  const [loginOpen, setLoginOpen] = useState(false);

  useLenis(reducedMotion);
  // Re-scan when the live dashboard mock swaps its placeholder for real bars.
  useReveal(rootRef, reducedMotion, [stats !== null]);

  const bars = toBars(stats?.versionCounts);
  const envRows = toEnvRows(stats?.versionCounts);
  const dash = "—";
  const statItems = [
    { value: stats?.totalVersions ?? dash, label: "Configuration versions stored" },
    { value: stats?.totalApplications ?? dash, label: "Applications in the vault" },
    { value: stats?.totalAuditEntries ?? stats?.auditLogCount ?? dash, label: "Audit entries recorded" },
    { value: stats?.totalEnvironments ?? dash, label: "Environments in use" },
  ];

  const secondaryAction =
    role === "admin" ? (
      <Link href="/access" className="btn btn-outline">Manage API keys</Link>
    ) : (
      <button type="button" className="btn btn-outline" onClick={() => setLoginOpen(true)} aria-haspopup="dialog">
        Admin login
      </button>
    );

  return (
    <div className="landing" ref={rootRef}>
      <PlanetCanvas reducedMotion={reducedMotion} />

      <div className="page" id="top">
        {/* SECTION 1 · HERO */}
        <header className="hero">
          <nav className="nav" aria-label="Main">
            <Link href="/" aria-label="Zero-Config Vault home"><Brand /></Link>
            <div className="nav-links">
              {NAV_LINKS.map((l) => (
                <a key={l.href} href={l.href}>{l.label}</a>
              ))}
            </div>
            <Link href="/dashboard" className="btn btn-ghost">Open dashboard</Link>
          </nav>

          <div className="hero-inner">
            <h1 className="hero-title" data-reveal style={rd(60)}>
              Ship Config Faster,<br />Not <em>Riskier</em>
            </h1>
            <p className="hero-sub" data-reveal style={rd(180)}>
              Versioned configuration and secrets for microservices, encrypted before they reach MongoDB and pushed
              live to every service the moment they change.
            </p>
            <div className="hero-actions" data-reveal style={rd(300)}>
              <Link href="/dashboard" className="btn btn-primary">
                Open the dashboard <ArrowIcon />
              </Link>
              {secondaryAction}
            </div>
          </div>
        </header>

        <div className="logos" data-reveal style={rd(420)}>
          <div className="logos-label">Built on</div>
          <div className="logos-row">
            {BUILT_ON.map((name) => (
              <span key={name} className="logo">{name}</span>
            ))}
          </div>
        </div>

        {/* SECTION 2 · FEATURES */}
        <section className="section features" id="features">
          <div className="section-head">
            <span className="eyebrow" data-reveal>Everything in orbit</span>
            <h2 data-reveal style={rd(80)}>One vault for every configuration and secret</h2>
            <p data-reveal style={rd(160)}>
              Stop scattering settings across environment files and chat threads. Zero-Config Vault versions,
              encrypts and distributes them from a single MongoDB-backed service.
            </p>
          </div>
          <div className="feature-grid">
            {FEATURES.map((f, i) => (
              <article key={f.title} className="card" data-reveal style={rd(i * 90)}>
                <div className="card-icon"><FeatureIcon name={f.icon} /></div>
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </article>
            ))}
          </div>
        </section>

        {/* SECTION 3 · SHOWCASE + STATS */}
        <section className="section showcase" id="solutions">
          <div className="showcase-grid">
            <div className="showcase-copy">
              <span className="eyebrow" data-reveal>Built for momentum</span>
              <h2 data-reveal style={rd(90)}>See every version move in real time</h2>
              <p data-reveal style={rd(180)}>
                Every application, environment and revision, read live from the vault. Change a value and every
                subscribed service hears about it through a MongoDB change stream within moments.
              </p>
              <div data-reveal style={rd(270)}>
                <Link href="/configs" className="btn btn-primary">
                  Explore the configurations <ArrowIcon />
                </Link>
              </div>
            </div>

            <div className="dashboard" data-reveal style={rd(160)} aria-label="Live versions per application">
              <div className="dash-top">
                <span>Versions per app</span>
                <span className="dash-live">{stats ? "Live" : "Connecting"}</span>
              </div>
              {bars.length > 0 ? (
                <div className="dash-bars">
                  {bars.map((b, i) => (
                    <div
                      key={b.label}
                      className="dash-bar"
                      style={{ height: `${b.pct}%`, "--bd": `${i * 90}ms` } as React.CSSProperties}
                      title={`${b.label}: ${b.pct}% of the busiest app`}
                    >
                      <span className="dash-bar-label">{b.label}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="dash-empty">Waiting for the vault…</div>
              )}
              <div className="dash-rows">
                {envRows.map((row) => (
                  <div key={row.label} className="dash-row">
                    <span>{row.label}</span>
                    <div className="dash-track">
                      <div className="dash-fill" style={{ "--w": `${row.pct}%` } as React.CSSProperties} />
                    </div>
                    <span className="dash-row-pct">{row.pct}%</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="stats" id="stats">
            {statItems.map((s, i) => (
              <div key={s.label} className="stat" data-reveal style={rd(i * 80)}>
                <div className="stat-value">{s.value}</div>
                <div className="stat-label">{s.label}</div>
              </div>
            ))}
          </div>
        </section>

        {/* SECTION 4 · CTA + FOOTER */}
        <section className="section cta" id="cta">
          <div className="cta-card">
            <span className="eyebrow" data-reveal style={rd(60)}>Ready when you are</span>
            <h2 data-reveal style={rd(140)}>Put your configuration on a new trajectory</h2>
            <p data-reveal style={rd(220)}>
              Browse as a guest straight away: secrets stay masked, and you can edit the playground app. Sign in as
              admin to reveal secrets and manage API keys.
            </p>
            <div className="hero-actions" data-reveal style={rd(300)}>
              <Link href="/dashboard" className="btn btn-primary">
                Open the dashboard <ArrowIcon />
              </Link>
              {secondaryAction}
            </div>
          </div>

          <footer className="footer">
            <div className="footer-brand">
              <Brand />
              <p>Versioned configuration and secrets, stored only in MongoDB.</p>
            </div>
            <div className="footer-cols">
              {FOOTER_COLUMNS.map((col) => (
                <div key={col.title} className="footer-col">
                  <h4>{col.title}</h4>
                  <ul>
                    {col.links.map((l) => (
                      <li key={l.href}><Link href={l.href}>{l.label}</Link></li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </footer>
          <p className="copyright">© {new Date().getFullYear()} Zero-Config Vault — Built among the stars.</p>
        </section>
      </div>

      <LoginDialog open={loginOpen} onClose={() => setLoginOpen(false)} onSignedIn={() => router.push("/dashboard")} />
    </div>
  );
}
