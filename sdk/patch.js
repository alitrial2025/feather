const fs = require('fs');
let content = fs.readFileSync('src/commonMain/kotlin/dev/feather/sdk/Feather.kt', 'utf8');

if (!content.includes('import kotlinx.serialization.Serializable')) {
    content = 'import kotlinx.serialization.Serializable\nimport kotlinx.serialization.SerialName\n\n' + content;
}

content = content.replace(/^data class (\w+)/gm, '@Serializable\ndata class $1');
content = content.replace(/^class (\w+)\s*\(/gm, '@Serializable\n@SerialName("$1")\nclass $1(');
content = content.replace(/^interface Widget/gm, '@Serializable\nsealed interface Widget');

fs.writeFileSync('src/commonMain/kotlin/dev/feather/sdk/Feather.kt', content);
console.log('Patched Feather.kt');
