// Reject the example storage configuration that made staging uploads fail.
import { readFileSync } from 'node:fs';
import { parseEnv } from 'node:util';

const configured = parseEnv(readFileSync(process.argv[2], 'utf8'));
const example = parseEnv(readFileSync('.env.example', 'utf8'));
const invalid = ['CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET']
    .filter(key => !configured[key]?.trim() || configured[key] === example[key]);
if (invalid.length) {
    console.error('Configure real staging storage credentials before deployment: ' + invalid.join(', '));
    process.exit(1);
}
console.log('Staging storage configuration contains non-example values.');
