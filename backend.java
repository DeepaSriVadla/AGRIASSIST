import { NextResponse } from "next/server"
import { compare } from "bcrypt"
import { sign } from "jsonwebtoken"
import { prisma } from "@/lib/prisma"
import { cookies } from "next/headers"

export async function POST(request: Request) {
  try {
    const { email, password } = await request.json()

    // Validate input
    if (!email || !password) {
      return NextResponse.json({ error: "Email and password are required" }, { status: 400 })
    }

    // Find user
    const user = await prisma.user.findUnique({
      where: { email },
    })

    if (!user) {
      return NextResponse.json({ error: "Invalid email or password" }, { status: 401 })
    }

    // Verify password
    const passwordMatch = await compare(password, user.password)

    if (!passwordMatch) {
      return NextResponse.json({ error: "Invalid email or password" }, { status: 401 })
    }

    // Generate JWT token
    const token = sign({ id: user.id, email: user.email }, process.env.JWT_SECRET || "fallback_secret", {
      expiresIn: "7d",
    })

    // Set cookie
    cookies().set({
      name: "auth_token",
      value: token,
      httpOnly: true,
      path: "/",
      secure: process.env.NODE_ENV === "production",
      maxAge: 60 * 60 * 24 * 7, // 7 days
    })

    // Return user without password
    const { password: _, ...userWithoutPassword } = user

    return NextResponse.json({
      message: "Login successful",
      user: userWithoutPassword,
    })
  } catch (error) {
    console.error("Login error:", error)
    return NextResponse.json({ error: "Internal server error" }, { status: 500 })
  }
}

import { NextResponse } from "next/server"
import { cookies } from "next/headers"

export async function POST() {
  // Clear the auth cookie
  cookies().set({
    name: "auth_token",
    value: "",
    httpOnly: true,
    path: "/",
    secure: process.env.NODE_ENV === "production",
    maxAge: 0,
  })

  return NextResponse.json({ message: "Logged out successfully" })
}

import { NextResponse } from "next/server"
import { verify } from "jsonwebtoken"
import { cookies } from "next/headers"
import { prisma } from "@/lib/prisma"

export async function GET() {
  try {
    // Get token from cookies
    const token = cookies().get("auth_token")?.value

    if (!token) {
      return NextResponse.json({ error: "Unauthorized" }, { status: 401 })
    }

    // Verify token
    const decoded = verify(token, process.env.JWT_SECRET || "fallback_secret") as { id: string }

    // Get user from database
    const user = await prisma.user.findUnique({
      where: { id: decoded.id },
    })

    if (!user) {
      return NextResponse.json({ error: "User not found" }, { status: 404 })
    }

    // Return user without password
    const { password, ...userWithoutPassword } = user

    return NextResponse.json({ user: userWithoutPassword })
  } catch (error) {
    console.error("Auth error:", error)
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 })
  }
}

import { NextResponse } from "next/server"
import { hash } from "bcrypt"
import { prisma } from "@/lib/prisma"

export async function POST(request: Request) {
  try {
    const { name, email, phone, password } = await request.json()

    // Validate input
    if (!name || !email || !phone || !password) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 })
    }

    // Check if user already exists
    const existingUser = await prisma.user.findUnique({
      where: { email },
    })

    if (existingUser) {
      return NextResponse.json({ error: "Email already registered" }, { status: 409 })
    }

    // Hash password
    const hashedPassword = await hash(password, 10)

    // Create user
    const user = await prisma.user.create({
      data: {
        name,
        email,
        phone,
        password: hashedPassword,
      },
    })

    // Return user without password
    const { password: _, ...userWithoutPassword } = user

    return NextResponse.json({ message: "User registered successfully", user: userWithoutPassword }, { status: 201 })
  } catch (error) {
    console.error("Registration error:", error)
    return NextResponse.json({ error: "Internal server error" }, { status: 500 })
  }
}

import { NextResponse } from "next/server"

// Crop recommendation data
const cropRecommendations = {
  clay: {
    summer: ["Rice", "Wheat", "Cotton"],
    winter: ["Wheat", "Mustard", "Peas"],
    monsoon: ["Rice", "Corn", "Sugarcane"],
  },
  sandy: {
    summer: ["Groundnut", "Watermelon", "Cucumber"],
    winter: ["Carrots", "Potatoes", "Beans"],
    monsoon: ["Corn", "Millet", "Beans"],
  },
  loamy: {
    summer: ["Vegetables", "Cotton", "Corn"],
    winter: ["Wheat", "Vegetables", "Pulses"],
    monsoon: ["Rice", "Vegetables", "Sugarcane"],
  },
  black: {
    summer: ["Cotton", "Sugarcane", "Sunflower"],
    winter: ["Wheat", "Chickpeas", "Sorghum"],
    monsoon: ["Cotton", "Soybeans", "Pulses"],
  },
  red: {
    summer: ["Groundnut", "Millet", "Cotton"],
    winter: ["Pulses", "Vegetables", "Oilseeds"],
    monsoon: ["Rice", "Corn", "Pulses"],
  },
}

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url)
    const soilType = searchParams.get("soil")
    const season = searchParams.get("season")

    // If no parameters are provided, return all recommendations
    if (!soilType && !season) {
      return NextResponse.json(cropRecommendations)
    }

    // If only soil type is provided
    if (soilType && !season) {
      if (!cropRecommendations[soilType as keyof typeof cropRecommendations]) {
        return NextResponse.json({ error: "Invalid soil type" }, { status: 400 })
      }
      return NextResponse.json(cropRecommendations[soilType as keyof typeof cropRecommendations])
    }

    // If only season is provided
    if (!soilType && season) {
      const seasonRecommendations: Record<string, string[]> = {}

      Object.keys(cropRecommendations).forEach((soil) => {
        const soilData = cropRecommendations[soil as keyof typeof cropRecommendations]
        if (soilData[season as keyof typeof soilData]) {
          seasonRecommendations[soil] = soilData[season as keyof typeof soilData]
        }
      })

      if (Object.keys(seasonRecommendations).length === 0) {
        return NextResponse.json({ error: "Invalid season" }, { status: 400 })
      }

      return NextResponse.json(seasonRecommendations)
    }

    // If both soil type and season are provided
    if (soilType && season) {
      const soilData = cropRecommendations[soilType as keyof typeof cropRecommendations]

      if (!soilData) {
        return NextResponse.json({ error: "Invalid soil type" }, { status: 400 })
      }

      const crops = soilData[season as keyof typeof soilData]

      if (!crops) {
        return NextResponse.json({ error: "Invalid season" }, { status: 400 })
      }

      return NextResponse.json({ crops })
    }

    return NextResponse.json({ error: "Invalid parameters" }, { status: 400 })
  } catch (error) {
    console.error("Crops API error:", error)
    return NextResponse.json({ error: "Internal server error" }, { status: 500 })
  }
}

import { NextResponse } from "next/server"
import { prisma } from "@/lib/prisma"

export async function GET() {
  try {
    // Get market prices from database
    const marketPrices = await prisma.marketPrice.findMany({
      orderBy: {
        updatedAt: "desc",
      },
    })

    return NextResponse.json(marketPrices)
  } catch (error) {
    console.error("Market API error:", error)
    return NextResponse.json({ error: "Failed to fetch market prices" }, { status: 500 })
  }
}

// Admin endpoint to update market prices
export async function POST(request: Request) {
  try {
    const { vegetable, price, market } = await request.json()

    // Validate input
    if (!vegetable || !price || !market) {
      return NextResponse.json({ error: "Missing required fields" }, { status: 400 })
    }

    // Update or create market price
    const marketPrice = await prisma.marketPrice.upsert({
      where: {
        vegetableMarket: {
          vegetable,
          market,
        },
      },
      update: {
        price,
        updatedAt: new Date(),
      },
      create: {
        vegetable,
        price,
        market,
        updatedAt: new Date(),
      },
    })

    return NextResponse.json(marketPrice)
  } catch (error) {
    console.error("Market API error:", error)
    return NextResponse.json({ error: "Failed to update market price" }, { status: 500 })
  }
}

import { NextResponse } from "next/server"

// Soil data
const soilData = {
  clay: {
    ph: "6.0-7.0",
    characteristics: "Heavy, sticky when wet, hard when dry",
    maintenance: "Add organic matter, avoid overwatering",
  },
  sandy: {
    ph: "6.0-6.5",
    characteristics: "Light, well-draining, warms quickly",
    maintenance: "Add organic matter, frequent watering",
  },
  loamy: {
    ph: "6.0-7.0",
    characteristics: "Perfect balance, ideal for most crops",
    maintenance: "Regular organic matter addition",
  },
  black: {
    ph: "6.5-7.5",
    characteristics: "Rich in nutrients, good water retention",
    maintenance: "Proper drainage management",
  },
  red: {
    ph: "6.0-6.5",
    characteristics: "Iron-rich, well-draining",
    maintenance: "Add organic matter, manage pH",
  },
}

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url)
    const soilType = searchParams.get("type")

    // If no soil type is provided, return all soil types
    if (!soilType) {
      return NextResponse.json(soilData)
    }

    // Check if the requested soil type exists
    if (!soilData[soilType as keyof typeof soilData]) {
      return NextResponse.json({ error: "Invalid soil type" }, { status: 400 })
    }

    // Return data for the requested soil type
    return NextResponse.json(soilData[soilType as keyof typeof soilData])
  } catch (error) {
    console.error("Soil API error:", error)
    return NextResponse.json({ error: "Internal server error" }, { status: 500 })
  }
}

import { NextResponse } from "next/server"

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url)
    const city = searchParams.get("city")

    if (!city) {
      return NextResponse.json({ error: "City parameter is required" }, { status: 400 })
    }

    const API_KEY = process.env.OPENWEATHER_API_KEY

    if (!API_KEY) {
      return NextResponse.json({ error: "Weather API key not configured" }, { status: 500 })
    }

    const response = await fetch(
      `https://api.openweathermap.org/data/2.5/weather?q=${city}&appid=${API_KEY}&units=metric`,
    )

    if (!response.ok) {
      const errorData = await response.json()
      return NextResponse.json(
        { error: errorData.message || "Failed to fetch weather data" },
        { status: response.status },
      )
    }

    const data = await response.json()

    return NextResponse.json(data)
  } catch (error) {
    console.error("Weather API error:", error)
    return NextResponse.json({ error: "Failed to fetch weather data" }, { status: 500 })
  }
}

@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  --foreground-rgb: 0, 0, 0;
  --background-rgb: 240, 248, 240;
  --background: 0 0% 100%;
  --foreground: 0 0% 3.9%;
  --card: 0 0% 100%;
  --card-foreground: 0 0% 3.9%;
  --popover: 0 0% 100%;
  --popover-foreground: 0 0% 3.9%;
  --primary: 0 0% 9%;
  --primary-foreground: 0 0% 98%;
  --secondary: 0 0% 96.1%;
  --secondary-foreground: 0 0% 9%;
  --muted: 0 0% 96.1%;
  --muted-foreground: 0 0% 45.1%;
  --accent: 0 0% 96.1%;
  --accent-foreground: 0 0% 9%;
  --destructive: 0 84.2% 60.2%;
  --destructive-foreground: 0 0% 98%;
  --border: 0 0% 89.8%;
  --input: 0 0% 89.8%;
  --ring: 0 0% 3.9%;
  --chart-1: 12 76% 61%;
  --chart-2: 173 58% 39%;
  --chart-3: 197 37% 24%;
  --chart-4: 43 74% 66%;
  --chart-5: 27 87% 67%;
  --radius: 0.5rem;
  --sidebar-background: 0 0% 98%;
  --sidebar-foreground: 240 5.3% 26.1%;
  --sidebar-primary: 240 5.9% 10%;
  --sidebar-primary-foreground: 0 0% 98%;
  --sidebar-accent: 240 4.8% 95.9%;
  --sidebar-accent-foreground: 240 5.9% 10%;
  --sidebar-border: 220 13% 91%;
  --sidebar-ring: 217.2 91.2% 59.8%;
}
body {
  color: rgb(var(--foreground-rgb));
  background: rgb(var(--background-rgb));
  font-family: Arial, Helvetica, sans-serif;
}

.dark {
  --background: 0 0% 3.9%;
  --foreground: 0 0% 98%;
  --card: 0 0% 3.9%;
  --card-foreground: 0 0% 98%;
  --popover: 0 0% 3.9%;
  --popover-foreground: 0 0% 98%;
  --primary: 0 0% 98%;
  --primary-foreground: 0 0% 9%;
  --secondary: 0 0% 14.9%;
  --secondary-foreground: 0 0% 98%;
  --muted: 0 0% 14.9%;
  --muted-foreground: 0 0% 63.9%;
  --accent: 0 0% 14.9%;
  --accent-foreground: 0 0% 98%;
  --destructive: 0 62.8% 30.6%;
  --destructive-foreground: 0 0% 98%;
  --border: 0 0% 14.9%;
  --input: 0 0% 14.9%;
  --ring: 0 0% 83.1%;
  --chart-1: 220 70% 50%;
  --chart-2: 160 60% 45%;
  --chart-3: 30 80% 55%;
  --chart-4: 280 65% 60%;
  --chart-5: 340 75% 55%;
  --sidebar-background: 240 5.9% 10%;
  --sidebar-foreground: 240 4.8% 95.9%;
  --sidebar-primary: 224.3 76.3% 48%;
  --sidebar-primary-foreground: 0 0% 100%;
  --sidebar-accent: 240 3.7% 15.9%;
  --sidebar-accent-foreground: 240 4.8% 95.9%;
  --sidebar-border: 240 3.7% 15.9%;
  --sidebar-ring: 217.2 91.2% 59.8%;
}

@layer utilities {
  .text-balance {
    text-wrap: balance;
  }
}

@layer base {
  * {
    @apply border-border;
  }
  body {
    @apply bg-background text-foreground;
  }
}

import type React from "react"
import "./globals.css"
import type { Metadata } from "next"
import { Inter } from "next/font/google"

const inter = Inter({ subsets: ["latin"] })

export const metadata: Metadata = {
  title: "AgriAssist Backend",
  description: "Backend server for the AgriAssist application",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>{children}</body>
    </html>
  )
}

import Link from "next/link"

export default function Home() {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-4 bg-[#f0f8f0]">
      <div className="w-full max-w-4xl p-8 bg-white rounded-lg shadow-md">
        <h1 className="text-4xl font-bold text-center text-[#4CAF50] mb-8">Welcome to AgriAssist Backend</h1>
        <p className="text-lg text-center mb-8">
          This is the backend server for the AgriAssist application. The API endpoints are available at /api/*.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="p-6 bg-[#f0f8f0] rounded-lg shadow">
            <h2 className="text-2xl font-semibold text-[#4CAF50] mb-4">API Endpoints</h2>
            <ul className="space-y-2 list-disc pl-5">
              <li>
                Authentication: <code>/api/auth/*</code>
              </li>
              <li>
                Weather: <code>/api/weather</code>
              </li>
              <li>
                Soil Analysis: <code>/api/soil</code>
              </li>
              <li>
                Market Prices: <code>/api/market</code>
              </li>
              <li>
                Crop Recommendations: <code>/api/crops</code>
              </li>
            </ul>
          </div>
          <div className="p-6 bg-[#f0f8f0] rounded-lg shadow">
            <h2 className="text-2xl font-semibold text-[#4CAF50] mb-4">Documentation</h2>
            <p className="mb-4">For detailed API documentation and usage examples, please refer to the links below:</p>
            <div className="space-y-2">
              <Link
                href="/api-docs"
                className="block p-2 bg-[#4CAF50] text-white text-center rounded hover:bg-[#45a049]"
              >
                API Documentation
              </Link>
              <Link
                href="https://github.com/yourusername/agri-assist"
                className="block p-2 bg-[#4CAF50] text-white text-center rounded hover:bg-[#45a049]"
              >
                GitHub Repository
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

import { PrismaClient } from "@prisma/client"

// PrismaClient is attached to the `global` object in development to prevent
// exhausting your database connection limit.
const globalForPrisma = global as unknown as { prisma: PrismaClient }

export const prisma = globalForPrisma.prisma || new PrismaClient()

if (process.env.NODE_ENV !== "production") globalForPrisma.prisma = prisma

// This is your Prisma schema file,
// learn more about it in the docs: https://pris.ly/d/prisma-schema

generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

model User {
  id        String   @id @default(cuid())
  name      String
  email     String   @unique
  phone     String
  password  String
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
}

model MarketPrice {
  id        String   @id @default(cuid())
  vegetable String
  price     Float
  market    String
  updatedAt DateTime @default(now())

  @@unique([vegetable, market], name: "vegetableMarket")
}

# Database
DATABASE_URL="postgresql://username:password@localhost:5432/agriassist?schema=public"

# Authentication
JWT_SECRET="your-secret-key-here"

# OpenWeather API
OPENWEATHER_API_KEY="your-openweather-api-key"

import { NextResponse } from "next/server"
import type { NextRequest } from "next/server"
import { verify } from "jsonwebtoken"

// Protected routes that require authentication
const protectedRoutes = [
  "/api/auth/me",
  // Add other protected routes here
]

export function middleware(request: NextRequest) {
  const path = request.nextUrl.pathname

  // Check if the path is in the protected routes
  if (protectedRoutes.some((route) => path.startsWith(route))) {
    const token = request.cookies.get("auth_token")?.value

    if (!token) {
      return NextResponse.json({ error: "Unauthorized" }, { status: 401 })
    }

    try {
      // Verify the token
      verify(token, process.env.JWT_SECRET || "fallback_secret")
      return NextResponse.next()
    } catch (error) {
      return NextResponse.json({ error: "Unauthorized" }, { status: 401 })
    }
  }

  return NextResponse.next()
}

export const config = {
  matcher: ["/api/:path*"],
}

/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ["class"],
  content: [
    "app/**/*.{ts,tsx}",
    "components/**/*.{ts,tsx}",
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        primary: "#4CAF50",
        secondary: "#45a049",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}

