API="https://api.bintray.com"
PROPS=";bt_package=update;bt_version=version;publish=1;override=1"
FOLDER="$TRAVIS_BUILD_DIR/releng/de.bund.bfr.knime.update/target/repository"

# DEPLOY TO test repo
# =====================================================================================================
# Delete previous version 'version'
curl -u $BINTRAY_USER:$BINTRAY_KEY -X DELETE $API/packages/silebat/nightly/update/versions/version

# Create version 'version'
curl -u $BINTRAY_USER:$BINTRAY_KEY -H "Content-Type: application/json" -X POST -d '{"name": "version", "description": ""}' $API/packages/silebat/nightly/update/versions

# Upload artifacts and content
curl -u $BINTRAY_USER:$BINTRAY_KEY -T $FOLDER/artifacts.jar $API/content/silebat/nightly/artifacts.jar$PROPS
curl -u $BINTRAY_USER:$BINTRAY_KEY -T $FOLDER/content.jar $API/content/silebat/nightly/content.jar$PROPS

# Upload features (feature includes the features/ folder)
for feature in $FOLDER/features/*.jar; do
	file=$(basename -- "$feature")
	curl -u $BINTRAY_USER:$BINTRAY_KEY -T $feature $API/content/silebat/nightly/features/$file$PROPS
done

# Upload plugins (plugin includes the plugins/ folder)
for plugin in $FOLDER/plugins/*.jar; do
	file=$(basename -- "$plugin")
	curl -u $BINTRAY_USER:$BINTRAY_KEY -T $plugin $API/content/silebat/nightly/plugins/$file$PROPS
done
