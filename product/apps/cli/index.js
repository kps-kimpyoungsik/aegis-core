import crypto from "node:crypto";
import { createTask } from "@aegis/core-domain";

const [,, command, goal = "bootstrap task"] = process.argv;

if (command === "task:new") {
  console.log(JSON.stringify(createTask({ id: crypto.randomUUID(), goal, owner: "cli" }), null, 2));
} else {
  console.log("usage: node apps/cli/index.js task:new [goal]");
}
