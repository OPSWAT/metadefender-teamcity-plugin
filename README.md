# metadefender-teamcity-plugin

MetaDefender TeamCity plugin will scan your build with 30+ leading anti-malware engines to detect possible malware & will alert if any anti-virus engines are incorrectly flagging your software or application as malicious.

For more details setup & usage please see: https://www.opswat.com/free-tools/teamcity-plugin

## Installation

Download and install the newest package from the [TeamCity plugin page](https://plugins.jetbrains.com/plugin/11110-opswat-metadefender).

### TeamCity 2020 CRSF protection

For TeamCity 2020, in case you're receiving:
> Responding with 403 status code due to failed CSRF check: authenticated POST request is made, but neither tc-csrf-token parameter nor X-TC-CSRF-Token header are provided.. For a temporary workaround, you can set internal property teamcity.csrf.paranoid=false  and provide valid Origin=http://localhost:8090 header with your request

Update Server Administrator > Internal Properties, by setting the following 2 values:
```
teamcity.csrf.paranoid=false
teamcity.csrf.origin.check.enabled=logOnly
```

## Build

Easiest way to build is by using the [dockerfile](https://github.com/OPSWAT/metadefender-teamcity-plugin/blob/master/dockerfile):

```bash
./build.sh
```

This will create a docker image containing the build result & copy the metadefender-plugin.zip.

## Support

For specific product questions or issues please contact [support](https://www.opswat.com/support).

## License

[MIT](https://github.com/OPSWAT/metadefender-cloudformation/blob/master/LICENSE)