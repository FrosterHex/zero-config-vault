import { redirect } from "next/navigation";

// Temporary until the landing page replaces it.
export default function Root() {
  redirect("/dashboard");
}
