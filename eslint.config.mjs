import tsParser from "@typescript-eslint/parser";
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default [{
    ignores: [
        "**/cypress",
        "**/cypress.*",
        "**/node_modules/*",
        "**/dist/*",
        "**/*.html",
        "coverage/**/*",
        "**/**/__tests__",
        "**/.eslintrc.js",
        "**/generated/*",
        "**/clientlib-site/*",
        "**/clientlib-dependencies/*",
        "**/clientlib-forms-react/*",
    ],
}, ...compat.extends("eslint:recommended"), {
    languageOptions: {
        globals: {
            Granite: "readonly",
            document: "readonly",
            window: "readonly",
            Coral: "readonly",
            jQuery: "readonly",
            Dam: "readonly",
            _g: "readonly",
            self: "readonly",
            location: "readonly",
            setInterval: "readonly",
            DamJavascriptUtils: "readonly",
            setTimeout: "readonly",
            event: "readonly",
            UNorm: "readonly",
            Class: "readonly",
            URL: "readonly",
            FormData: "readonly",
            clearInterval: "readonly",
            console: "readonly",
            XMLHttpRequest: "readonly",
            HFT: "writable",
            process: "readonly",
            module: "readonly"
        },

        parser: tsParser,
        ecmaVersion: "latest",
        sourceType: "script",
    },

    rules: {
        "no-console": ["error", {
            allow: ["warn", "error"],
        }],

        "no-unused-vars": 0,
        "no-prototype-builtins": 0,
    },
}];