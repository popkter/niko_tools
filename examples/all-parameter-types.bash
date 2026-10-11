# 脚本类型：Bash。var 声明由插件解析，执行前会自动移除。
# 十种显式参数类型
var text=${单行文本@text:示例文本}
var multiline=${多行文本@multiline:多行文本}
var integer=${重试次数@integer:3}
var number=${比例@number:0.5}
var boolean=${启用缓存@boolean:true}
var choice=${下拉框@choice:选项1=1|选项2=2}
var file=${选择文件@file:1}
var dir=${选择目录@dir:2}
var secret=${访问令牌@secret:1009}
var radio=${单选框@radio:选项1=1|选项2=2}

# 引用声明的内部名称；相同参数可多次引用。
echo "单行文本：${text}"
echo "多行文本：${multiline}"
echo "整数：${integer}"
echo "数字：${number}"
echo "复选框：${boolean}"
echo "下拉框实际值：${choice}"
echo "文件路径：${file}"
echo "目录路径：${dir}"
echo "单选框实际值：${radio}"
echo "再次引用文件路径：${file}"

# 密码只展示是否已填写，不在输出中显示实际内容。
if [ -n "${secret}" ]; then
    echo "访问令牌：已填写（隐藏内容）${secret}"
fi
