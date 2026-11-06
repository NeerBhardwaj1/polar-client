#version 120

// Polar Client motion blur: while the camera turns, each pixel is blurred along the path it moves on screen during
// the camera's "shutter" (all inside this one frame, so no ghost copies of old frames). Numbers from
// dev.polarclient.render.CameraBlur: Turn* is the rotation (columns) from a view direction now to where it pointed when
// the shutter opened; Lens is (tangent of half the horizontal / vertical field of view, longest blur, unused).
uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 InSize;
uniform vec3 TurnX;
uniform vec3 TurnY;
uniform vec3 TurnZ;
uniform vec4 Lens;

const int SAMPLES = 16;

void main() {
    mat3 turn = mat3(TurnX, TurnY, TurnZ);
    vec2 lens = Lens.xy;
    float maxLength = Lens.z;

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 then = turn * vec3(ndc * lens, -1.0);
    vec2 path = (then.xy / max(-then.z, 0.05) / lens - ndc) * 0.5;
    float len = length(path);
    if (len > maxLength) {
        path *= maxLength / len;
        len = maxLength;
    }
    if (len * max(InSize.x, InSize.y) < 0.5) {
        gl_FragColor = vec4(texture2D(DiffuseSampler, texCoord).rgb, 1.0);
        return;
    }
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 sum = vec3(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) + jitter) / float(SAMPLES) - 0.5;
        sum += texture2D(DiffuseSampler, clamp(texCoord + path * t, vec2(0.0), vec2(1.0))).rgb;
    }
    gl_FragColor = vec4(sum / float(SAMPLES), 1.0);
}
