export default [
  {
    ignores: ["node_modules/**", "dist/**", "coverage/**", "release/**"],
  },
  {
    files: ["**/*.{js,mjs,jsx,ts,tsx}"],
    rules: {
      "no-debugger": "error",
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-new-func": "error",
      "no-throw-literal": "error",
      "no-unused-vars": ["error", { "argsIgnorePattern": "^_", "varsIgnorePattern": "^_" }],
      "no-constant-condition": ["error", { "checkLoops": false }]
    }
  }
];
