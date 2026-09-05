export default [
  {
    ignores: ["node_modules/**", "dist/**", "coverage/**", "release/**"],
  },
  {
    files: ["**/*.{js,mjs,jsx,ts,tsx}"],
    rules: {
      "curly": ["error", "all"],
      "eqeqeq": ["error", "always"],
      "no-debugger": "error",
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-new-func": "error",
      "no-throw-literal": "error",
      "no-var": "error",
      "prefer-const": "error",
      "no-unused-vars": ["error", { "argsIgnorePattern": "^_", "varsIgnorePattern": "^_" }],
      "no-constant-condition": ["error", { "checkLoops": false }]
    }
  }
];
