import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { AppProviders } from "@/components/providers/AppProviders";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800", "900"],
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains-mono",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "Zero-Config Vault | Configuration and Secrets on MongoDB",
  description:
    "Versioned configuration and secret store built on MongoDB, with envelope encryption, change streams and audit logging.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // The font variables live on <html>: Tailwind's @theme reads them on :root (font-mono, font-sans),
    // where variables defined only on <body> would be undefined and fall back to the body font.
    <html lang="en" className={`dark ${inter.variable} ${jetbrainsMono.variable}`}>
      <body className="antialiased bg-[#04060f] text-[#f3f6ff]">
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
