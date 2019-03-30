# metadefender-teamcity-plugin

MetaDefender TeamCity plugin will scan your build with 30+ leading anti-malware engines to detect possible malware & will alert if any anti-virus engines are incorrectly flagging your software or application as malicious.

For more details setup & usage please see: https://www.opswat.com/free-tools/teamcity-plugin

## Installation

Download and install the newest package from the [TeamCity plugin page](https://plugins.jetbrains.com/plugin/11110-opswat-metadefender).

## Build

Easiest way to build is by using the [dockerfile](https://github.com/OPSWAT/metadefender-teamcity-plugin/blob/master/dockerfile):

```bash
make build
```

This will create a docker image containing the build result & copy the metadefender-plugin.zip.

## Support

For specific product questions or issues please contact [support](https://www.opswat.com/support).

## License

[MIT](https://github.com/OPSWAT/metadefender-cloudformation/blob/master/LICENSE)