#version 330
#extension GL_ARB_separate_shader_objects : require

// Polar Client motion blur: while the camera turns, each pixel is blurred along the path it moves on screen during
// the camera's "shutter" (all inside this one frame, so no ghost copies of old frames). The numbers come from
// dev.polarclient.render.CameraBlur in a 16x1 texture: each texel is one float, its four bytes in r, g, b and a.
//   0-8: rotation, column by column, from a view direction now to where it pointed when the shutter opened
//   9, 10: tangent of half the horizontal / vertical field of view; 11: longest blur, as a fraction of the screen
uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int SAMPLES = 16;

float param(int i) {
    uvec4 b = uvec4(texelFetch(ParamsSampler, ivec2(i, 0), 0) * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8) | (b.b << 16) | (b.a << 24));
}

void main() {
    mat3 turn = mat3(param(0), param(1), param(2), param(3), param(4), param(5), param(6), param(7), param(8));
    vec2 lens = vec2(param(9), param(10));
    float maxLength = param(11);

    // where this pixel's view direction pointed when the shutter opened, on today's screen
    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 then = turn * vec3(ndc * lens, -1.0);
    vec2 path = (then.xy / max(-then.z, 0.05) / lens - ndc) * 0.5;
    float len = length(path);
    if (len > maxLength) {
        path *= maxLength / len;
        len = maxLength;
    }
    vec2 size = vec2(textureSize(InSampler, 0));
    if (len * max(size.x, size.y) < 0.5) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    // samples spread evenly along the path and centred on the pixel, each pixel's set shifted a little (a smooth
    // noise) so neighbouring pixels fill the gaps between samples instead of showing steps
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 sum = vec3(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) + jitter) / float(SAMPLES) - 0.5;
        sum += texture(InSampler, clamp(texCoord + path * t, vec2(0.0), vec2(1.0))).rgb;
    }
    fragColor = vec4(sum / float(SAMPLES), 1.0);
}
