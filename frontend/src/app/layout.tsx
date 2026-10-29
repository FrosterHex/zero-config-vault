import type { Metadata } from "next";
import { Cormorant_Garamond, Montserrat, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const cormorantGaramond = Cormorant_Garamond({
  variable: "--font-cormorant",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  display: "swap",
});

const montserrat = Montserrat({
  variable: "--font-montserrat",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
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

import { AppProviders } from "@/components/providers/AppProviders";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body
        className={`${cormorantGaramond.variable} ${montserrat.variable} ${jetbrainsMono.variable} antialiased bg-[#090D16] text-[#F8FAFC]`}
      >
        <AppProviders>
          {children}
        </AppProviders>
      </body>
    </html>
  );
}
